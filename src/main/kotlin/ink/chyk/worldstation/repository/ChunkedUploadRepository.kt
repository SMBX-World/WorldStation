package ink.chyk.worldstation.repository

import ink.chyk.worldstation.entity.UploadChunk
import ink.chyk.worldstation.entity.UploadSession
import ink.chyk.worldstation.enum.ChunkedUploadStatus
import ink.chyk.worldstation.enum.UploadFileKind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.less
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.util.UUID

data class UploadSessionRecord(
    val id: UUID,
    val userId: Int,
    val uploadKind: UploadFileKind,
    val fileName: String,
    val contentType: String,
    val totalSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val targetPath: String,
    val tempDir: String,
    val status: ChunkedUploadStatus,
    val savedChunkCount: Int,
    val finalUrl: String?,
    val error: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class UploadChunkRecord(
    val uploadId: UUID,
    val chunkIndex: Int,
    val size: Int,
    val sha256: String,
    val savedAtEpochMs: Long?,
)

@Repository
class ChunkedUploadRepository {
    fun createSession(record: UploadSessionRecord): UploadSessionRecord = transaction {
        UploadSession.insert {
            it[id] = record.id
            it[userId] = record.userId
            it[uploadKind] = record.uploadKind
            it[fileName] = record.fileName
            it[contentType] = record.contentType
            it[totalSize] = record.totalSize
            it[chunkSize] = record.chunkSize
            it[totalChunks] = record.totalChunks
            it[targetPath] = record.targetPath
            it[tempDir] = record.tempDir
            it[status] = record.status
            it[savedChunkCount] = record.savedChunkCount
            it[finalUrl] = record.finalUrl
            it[error] = record.error
            it[createdAtEpochMs] = record.createdAtEpochMs
            it[updatedAtEpochMs] = record.updatedAtEpochMs
            it[expiresAtEpochMs] = record.expiresAtEpochMs
        }
        record
    }

    fun getSession(id: UUID): UploadSessionRecord? = transaction {
        UploadSession.selectAll().where { UploadSession.id eq id }.firstOrNull()?.toSessionRecord()
    }

    fun listReceivedChunks(uploadId: UUID): List<Int> = transaction {
        UploadChunk.select(UploadChunk.chunkIndex)
            .where { UploadChunk.uploadId eq uploadId }
            .orderBy(UploadChunk.chunkIndex)
            .map { it[UploadChunk.chunkIndex] }
    }

    fun getChunk(uploadId: UUID, chunkIndex: Int): UploadChunkRecord? = transaction {
        UploadChunk.selectAll()
            .where { (UploadChunk.uploadId eq uploadId) and (UploadChunk.chunkIndex eq chunkIndex) }
            .firstOrNull()
            ?.toChunkRecord()
    }

    fun countContiguousChunks(uploadId: UUID, fromIndex: Int = 0): Int = transaction {
        val chunks = UploadChunk.select(UploadChunk.chunkIndex)
            .where { UploadChunk.uploadId eq uploadId }
            .orderBy(UploadChunk.chunkIndex)
            .map { it[UploadChunk.chunkIndex] }
            .toSet()
        var index = fromIndex
        while (chunks.contains(index)) index++
        index - fromIndex
    }

    fun countActiveSessionsForUser(userId: Int, activeStatuses: Collection<ChunkedUploadStatus>): Long = transaction {
        UploadSession.selectAll()
            .where { (UploadSession.userId eq userId) and (UploadSession.status inList activeStatuses) }
            .count()
    }

    fun saveChunk(uploadId: UUID, chunkIndex: Int, size: Int, sha256: String, now: Long) = transaction {
        val existing = UploadChunk.selectAll()
            .where { (UploadChunk.uploadId eq uploadId) and (UploadChunk.chunkIndex eq chunkIndex) }
            .firstOrNull()

        if (existing == null) {
            UploadChunk.insert {
                it[UploadChunk.uploadId] = uploadId
                it[UploadChunk.chunkIndex] = chunkIndex
                it[UploadChunk.size] = size
                it[UploadChunk.sha256] = sha256
                it[UploadChunk.receivedAtEpochMs] = now
                it[UploadChunk.savedAtEpochMs] = null
            }
        } else {
            UploadChunk.update({ (UploadChunk.uploadId eq uploadId) and (UploadChunk.chunkIndex eq chunkIndex) }) {
                it[UploadChunk.size] = size
                it[UploadChunk.sha256] = sha256
                it[UploadChunk.receivedAtEpochMs] = now
                it[UploadChunk.savedAtEpochMs] = null
            }
        }
    }

    fun updateStatus(uploadId: UUID, status: ChunkedUploadStatus, error: String? = null) = transaction {
        UploadSession.update({ UploadSession.id eq uploadId }) {
            it[UploadSession.status] = status
            it[UploadSession.error] = error
            it[UploadSession.updatedAtEpochMs] = System.currentTimeMillis()
        } > 0
    }

    fun markChunkSaved(uploadId: UUID, chunkIndex: Int, savedChunkCount: Int, now: Long) = transaction {
        UploadChunk.update({ (UploadChunk.uploadId eq uploadId) and (UploadChunk.chunkIndex eq chunkIndex) }) {
            it[savedAtEpochMs] = now
        }
        UploadSession.update({ UploadSession.id eq uploadId }) {
            it[UploadSession.savedChunkCount] = savedChunkCount
            it[UploadSession.updatedAtEpochMs] = now
        }
    }

    fun resetProcessing(uploadId: UUID, status: ChunkedUploadStatus, error: String?) = transaction {
        UploadChunk.update({ UploadChunk.uploadId eq uploadId }) {
            it[savedAtEpochMs] = null
        }
        UploadSession.update({ UploadSession.id eq uploadId }) {
            it[UploadSession.status] = status
            it[UploadSession.savedChunkCount] = 0
            it[UploadSession.error] = error
            it[UploadSession.updatedAtEpochMs] = System.currentTimeMillis()
        }
    }

    fun complete(uploadId: UUID, finalUrl: String) = transaction {
        val totalChunks = UploadSession.select(UploadSession.totalChunks)
            .where { UploadSession.id eq uploadId }
            .first()[UploadSession.totalChunks]
        UploadSession.update({ UploadSession.id eq uploadId }) {
            it[status] = ChunkedUploadStatus.COMPLETED
            it[savedChunkCount] = totalChunks
            it[UploadSession.finalUrl] = finalUrl
            it[error] = null
            it[updatedAtEpochMs] = System.currentTimeMillis()
        } > 0
    }

    fun expireSessions(now: Long): List<UploadSessionRecord> = transaction {
        val sessions = UploadSession.selectAll()
            .where {
                (UploadSession.expiresAtEpochMs less now) and
                    (UploadSession.status inList listOf(
                        ChunkedUploadStatus.CREATED,
                        ChunkedUploadStatus.RECEIVING,
                        ChunkedUploadStatus.CLIENT_UPLOAD_COMPLETED,
                        ChunkedUploadStatus.FAILED,
                    ))
            }
            .map { it.toSessionRecord() }

        UploadSession.update({
            (UploadSession.expiresAtEpochMs less now) and
                (UploadSession.status inList listOf(
                    ChunkedUploadStatus.CREATED,
                    ChunkedUploadStatus.RECEIVING,
                    ChunkedUploadStatus.CLIENT_UPLOAD_COMPLETED,
                    ChunkedUploadStatus.FAILED,
                ))
        }) {
            it[status] = ChunkedUploadStatus.EXPIRED
            it[updatedAtEpochMs] = now
        }
        sessions
    }

    private fun ResultRow.toSessionRecord() = UploadSessionRecord(
        id = this[UploadSession.id],
        userId = this[UploadSession.userId],
        uploadKind = this[UploadSession.uploadKind],
        fileName = this[UploadSession.fileName],
        contentType = this[UploadSession.contentType],
        totalSize = this[UploadSession.totalSize],
        chunkSize = this[UploadSession.chunkSize],
        totalChunks = this[UploadSession.totalChunks],
        targetPath = this[UploadSession.targetPath],
        tempDir = this[UploadSession.tempDir],
        status = this[UploadSession.status],
        savedChunkCount = this[UploadSession.savedChunkCount],
        finalUrl = this[UploadSession.finalUrl],
        error = this[UploadSession.error],
        createdAtEpochMs = this[UploadSession.createdAtEpochMs],
        updatedAtEpochMs = this[UploadSession.updatedAtEpochMs],
        expiresAtEpochMs = this[UploadSession.expiresAtEpochMs],
    )

    private fun ResultRow.toChunkRecord() = UploadChunkRecord(
        uploadId = this[UploadChunk.uploadId],
        chunkIndex = this[UploadChunk.chunkIndex],
        size = this[UploadChunk.size],
        sha256 = this[UploadChunk.sha256],
        savedAtEpochMs = this[UploadChunk.savedAtEpochMs],
    )
}
