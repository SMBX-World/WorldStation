package ink.chyk.worldstation.service

import ink.chyk.worldstation.configuration.UploadConfig
import ink.chyk.worldstation.dto.ChunkUploadDTO
import ink.chyk.worldstation.dto.ChunkedUploadDTO
import ink.chyk.worldstation.dto.CreateChunkedUploadRequestDTO
import ink.chyk.worldstation.enum.ChunkedUploadStatus
import ink.chyk.worldstation.repository.ChunkedUploadRepository
import ink.chyk.worldstation.repository.UploadSessionRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.math.min

class ChunkedUploadException(message: String) : RuntimeException(message)
class ChunkChecksumException(message: String) : RuntimeException(message)
class ChunkConflictException(message: String) : RuntimeException(message)
class ChunkWaitTimeoutException(message: String) : RuntimeException(message)

@Service
class ChunkedUploadService(
    private val config: UploadConfig,
    private val repository: ChunkedUploadRepository,
    private val storageService: StorageService,
    @Qualifier("storageUploadExecutor") private val storageUploadExecutor: Executor,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ChunkedUploadService::class.java)
        private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")
        private val contentRangePattern = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$")
    }

    private val activeProcessors = ConcurrentHashMap.newKeySet<UUID>()

    fun createSession(
        request: CreateChunkedUploadRequestDTO,
        principal: OAuth2User,
    ): ChunkedUploadDTO {
        if (request.totalSize <= 0) {
            throw ChunkedUploadException("文件大小不正确")
        }

        val fileName = cleanFileName(request.fileName)
        val userId = principal.getAttribute<Any>("id").toString().toInt()
        val activeCount = repository.countActiveSessionsForUser(
            userId,
            listOf(
                ChunkedUploadStatus.CREATED,
                ChunkedUploadStatus.RECEIVING,
                ChunkedUploadStatus.PROCESSING,
                ChunkedUploadStatus.CLIENT_UPLOAD_COMPLETED,
            )
        )
        if (activeCount >= config.maxActiveSessionsPerUser) {
            throw ChunkedUploadException("同时上传的文件过多，请稍后再试")
        }

        val prepared = storageService.prepareUpload(
            request.uploadKind,
            fileName,
            principal,
            request.contentType,
            request.totalSize,
        ).getOrElse { throw it }

        val now = System.currentTimeMillis()
        val uploadId = UUID.randomUUID()
        val totalChunks = ((request.totalSize + config.chunkSize - 1) / config.chunkSize).toInt()
        val tempDir = Path.of(config.tempDir).resolve(uploadId.toString()).normalize()
        Files.createDirectories(tempDir)

        val session = UploadSessionRecord(
            id = uploadId,
            userId = userId,
            uploadKind = request.uploadKind,
            fileName = fileName,
            contentType = prepared.contentType,
            totalSize = request.totalSize,
            chunkSize = config.chunkSize,
            totalChunks = totalChunks,
            targetPath = prepared.objectKey,
            tempDir = tempDir.toString(),
            status = ChunkedUploadStatus.CREATED,
            savedChunkCount = 0,
            finalUrl = null,
            error = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + config.sessionTtlHours * 60 * 60 * 1000,
        )
        return repository.createSession(session).toDTO()
    }

    fun uploadChunk(
        uploadId: UUID,
        chunkIndex: Int,
        contentLength: Long,
        contentRange: String,
        sha256: String,
        inputStream: InputStream,
        principal: OAuth2User,
    ): ChunkUploadDTO {
        val session = getOwnedSession(uploadId, principal)
        if (session.status in listOf(ChunkedUploadStatus.COMPLETED, ChunkedUploadStatus.ABORTED, ChunkedUploadStatus.EXPIRED)) {
            throw ChunkedUploadException("上传会话状态不允许继续上传")
        }
        validateChunkRequest(session, chunkIndex, contentLength, contentRange, sha256)

        val partPath = chunkPath(session, chunkIndex)
        val existing = repository.getChunk(uploadId, chunkIndex)
        if (existing != null && partPath.exists()) {
            if (existing.size == contentLength.toInt() && existing.sha256.equals(sha256, ignoreCase = true)) {
                maybeStartProcessing(uploadId, force = false)
                return ChunkUploadDTO(chunkIndex, received = true, status = repository.getSession(uploadId)!!.status)
            }
            throw ChunkConflictException("分块已存在但校验信息不一致")
        }

        val tmpPath = tempChunkPath(session, chunkIndex)
        tmpPath.deleteIfExists()
        Files.createDirectories(tmpPath.parent)

        val actualSha256 = writeChunkAndHash(inputStream, tmpPath, contentLength)
        if (!actualSha256.equals(sha256, ignoreCase = true)) {
            tmpPath.deleteIfExists()
            throw ChunkChecksumException("分块校验失败，请重传该分块")
        }

        moveAtomically(tmpPath, partPath)
        repository.saveChunk(uploadId, chunkIndex, contentLength.toInt(), actualSha256, System.currentTimeMillis())
        if (session.status == ChunkedUploadStatus.CREATED) {
            repository.updateStatus(uploadId, ChunkedUploadStatus.RECEIVING)
        }
        maybeStartProcessing(uploadId, force = false)

        return ChunkUploadDTO(
            chunkIndex = chunkIndex,
            received = true,
            status = repository.getSession(uploadId)?.status ?: ChunkedUploadStatus.RECEIVING,
        )
    }

    fun completeUpload(uploadId: UUID, principal: OAuth2User): ChunkedUploadDTO {
        val session = getOwnedSession(uploadId, principal)
        val contiguous = repository.countContiguousChunks(uploadId)
        if (contiguous < session.totalChunks) {
            throw ChunkedUploadException("仍有分块未上传完成")
        }
        if (session.status != ChunkedUploadStatus.COMPLETED) {
            repository.updateStatus(uploadId, ChunkedUploadStatus.CLIENT_UPLOAD_COMPLETED)
            maybeStartProcessing(uploadId, force = true)
        }
        return getSessionDTO(uploadId, principal)
    }

    fun getSessionDTO(uploadId: UUID, principal: OAuth2User): ChunkedUploadDTO {
        return getOwnedSession(uploadId, principal).toDTO()
    }

    fun abort(uploadId: UUID, principal: OAuth2User) {
        val session = getOwnedSession(uploadId, principal)
        repository.updateStatus(uploadId, ChunkedUploadStatus.ABORTED)
        deleteDirectory(Path.of(session.tempDir))
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun cleanupExpiredSessions() {
        val expired = repository.expireSessions(System.currentTimeMillis())
        expired.forEach {
            deleteDirectory(Path.of(it.tempDir))
        }
    }

    private fun maybeStartProcessing(uploadId: UUID, force: Boolean) {
        val session = repository.getSession(uploadId) ?: return
        if (session.status in listOf(ChunkedUploadStatus.COMPLETED, ChunkedUploadStatus.ABORTED, ChunkedUploadStatus.EXPIRED)) {
            return
        }
        if (!force) {
            val contiguous = repository.countContiguousChunks(uploadId)
            val threshold = min(config.streamStartWindowChunks, session.totalChunks)
            if (contiguous < threshold) return
        }
        if (!activeProcessors.add(uploadId)) return

        repository.updateStatus(uploadId, ChunkedUploadStatus.PROCESSING)
        storageUploadExecutor.execute {
            try {
                processUpload(uploadId)
            } finally {
                activeProcessors.remove(uploadId)
            }
        }
    }

    private fun processUpload(uploadId: UUID) {
        val session = repository.getSession(uploadId) ?: return
        val prepared = PreparedStorageUpload(
            objectKey = session.targetPath.trim('/'),
            contentType = session.contentType,
            contentLength = session.totalSize,
            finalUrl = storageService.finalUrlForKey(session.targetPath),
        )

        val result = ChunkSequenceInputStream(session).use { inputStream ->
            storageService.uploadPreparedFileStream(prepared, inputStream)
        }

        if (result.isSuccess) {
            repository.complete(uploadId, result.getOrThrow())
            deleteDirectory(Path.of(session.tempDir))
        } else {
            val allChunksReceived = repository.countContiguousChunks(uploadId) >= session.totalChunks
            val nextStatus = if (allChunksReceived) {
                ChunkedUploadStatus.FAILED
            } else {
                ChunkedUploadStatus.RECEIVING
            }
            val error = result.exceptionOrNull()?.message ?: "服务器处理文件失败"
            repository.resetProcessing(uploadId, nextStatus, error)
        }
    }

    private fun waitForVerifiedChunk(uploadId: UUID, chunkIndex: Int) {
        val deadline = System.currentTimeMillis() + config.chunkWaitTimeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            val session = repository.getSession(uploadId)
                ?: throw ChunkWaitTimeoutException("上传会话不存在")
            if (session.status == ChunkedUploadStatus.ABORTED) {
                throw ChunkWaitTimeoutException("上传已取消")
            }
            val chunk = repository.getChunk(uploadId, chunkIndex)
            if (chunk != null && chunkPath(session, chunkIndex).exists()) {
                return
            }
            Thread.sleep(500)
        }
        throw ChunkWaitTimeoutException("等待分块超时")
    }

    /**
     * 将已经校验的临时分块暴露为一条连续输入流，让 S3 PutObject 保持真正的流式上传。
     * 读取到分块边界时同步更新服务端处理进度。
     */
    private inner class ChunkSequenceInputStream(
        private val session: UploadSessionRecord,
    ) : InputStream() {
        private var chunkIndex = 0
        private var current: InputStream? = null
        private var currentRemaining = 0L
        private var closed = false

        override fun read(): Int {
            val singleByte = ByteArray(1)
            val read = read(singleByte, 0, 1)
            return if (read < 0) -1 else singleByte[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Stream is closed")
            if (length == 0) return 0
            if (chunkIndex >= session.totalChunks) return -1
            openCurrentChunk()

            val allowed = min(length.toLong(), currentRemaining).toInt()
            val read = current!!.read(buffer, offset, allowed)
            if (read < 0) throw IOException("分块 $chunkIndex 的实际大小小于校验记录")
            currentRemaining -= read
            if (currentRemaining == 0L) finishCurrentChunk()
            return read
        }

        private fun openCurrentChunk() {
            if (current != null) return
            waitForVerifiedChunk(session.id, chunkIndex)
            val chunk = repository.getChunk(session.id, chunkIndex)
                ?: throw IOException("分块 $chunkIndex 不存在")
            currentRemaining = chunk.size.toLong()
            current = Files.newInputStream(chunkPath(session, chunkIndex))
        }

        private fun finishCurrentChunk() {
            current?.close()
            current = null
            val completedIndex = chunkIndex
            chunkIndex++
            repository.markChunkSaved(session.id, completedIndex, chunkIndex, System.currentTimeMillis())
            if (!config.keepChunksUntilCompleted) {
                chunkPath(session, completedIndex).deleteIfExists()
            }
        }

        override fun close() {
            if (!closed) {
                closed = true
                current?.close()
                current = null
            }
        }
    }

    private fun getOwnedSession(uploadId: UUID, principal: OAuth2User): UploadSessionRecord {
        val session = repository.getSession(uploadId)
            ?: throw ChunkedUploadException("上传会话不存在")
        val userId = principal.getAttribute<Any>("id").toString().toInt()
        if (session.userId != userId) {
            throw ChunkedUploadException("无权访问该上传会话")
        }
        return session
    }

    private fun UploadSessionRecord.toDTO(): ChunkedUploadDTO {
        return ChunkedUploadDTO(
            uploadId = id,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            receivedChunks = repository.listReceivedChunks(id),
            savedChunkCount = savedChunkCount,
            status = status,
            finalUrl = finalUrl,
            error = error,
        )
    }

    private fun validateChunkRequest(
        session: UploadSessionRecord,
        chunkIndex: Int,
        contentLength: Long,
        contentRange: String,
        sha256: String,
    ) {
        if (chunkIndex !in 0 until session.totalChunks) {
            throw ChunkedUploadException("分块序号不正确")
        }
        if (!sha256Pattern.matches(sha256)) {
            throw ChunkedUploadException("分块校验码格式不正确")
        }

        val rangeMatch = contentRangePattern.matchEntire(contentRange)
            ?: throw ChunkedUploadException("Content-Range 格式不正确")
        val start = rangeMatch.groupValues[1].toLong()
        val end = rangeMatch.groupValues[2].toLong()
        val total = rangeMatch.groupValues[3].toLong()
        val expectedStart = chunkIndex.toLong() * session.chunkSize
        val expectedEnd = min(expectedStart + session.chunkSize, session.totalSize) - 1
        val expectedLength = expectedEnd - expectedStart + 1

        if (total != session.totalSize || start != expectedStart || end != expectedEnd) {
            throw ChunkedUploadException("Content-Range 与上传会话不匹配")
        }
        if (contentLength != expectedLength) {
            throw ChunkedUploadException("分块大小不正确")
        }
    }

    private fun writeChunkAndHash(inputStream: InputStream, tmpPath: Path, expectedLength: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val buffer = ByteArray(256 * 1024)

        Files.newOutputStream(tmpPath).use { output ->
            while (true) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                written += read
                if (written > expectedLength) {
                    throw ChunkedUploadException("分块大小超过声明长度")
                }
            }
        }

        if (written != expectedLength) {
            throw ChunkedUploadException("分块大小与声明长度不一致")
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun cleanFileName(fileName: String): String {
        val cleaned = fileName.trim()
        if (cleaned.isEmpty() || cleaned.contains('/') || cleaned.contains('\\') || cleaned.contains('\u0000')) {
            throw ChunkedUploadException("文件名不正确")
        }
        return cleaned
    }

    private fun chunkPath(session: UploadSessionRecord, chunkIndex: Int): Path {
        return Path.of(session.tempDir).resolve("%06d.part".format(chunkIndex)).normalize()
    }

    private fun tempChunkPath(session: UploadSessionRecord, chunkIndex: Int): Path {
        return Path.of(session.tempDir).resolve("%06d.part.tmp".format(chunkIndex)).normalize()
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteDirectory(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach {
                try {
                    Files.deleteIfExists(it)
                } catch (e: Exception) {
                    logger.warn("删除临时上传文件失败: {}", it, e)
                }
            }
        }
    }
}
