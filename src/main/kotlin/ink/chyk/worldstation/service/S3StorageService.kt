package ink.chyk.worldstation.service

import ink.chyk.worldstation.configuration.S3StorageProperties
import ink.chyk.worldstation.enum.UploadFileKind
import ink.chyk.worldstation.util.ContentTypeUtils
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class S3StorageService(
    private val properties: S3StorageProperties,
    private val s3: S3Client,
) : StorageService {
    companion object {
        private val logger = LoggerFactory.getLogger(S3StorageService::class.java)
        private const val PICBED_LIMIT: Long = 30 * 1024 * 1024
    }

    override fun prepareUpload(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String?,
        contentLength: Long,
    ): Result<PreparedStorageUpload> {
        return runCatching {
            val cleanedFileName = cleanFileName(fileName)
            if (uploadKind == UploadFileKind.PICBED && contentLength > PICBED_LIMIT) {
                throw FileSizeLimitExceededException("上传的文件大小超过限制", contentLength, PICBED_LIMIT)
            }

            val guessed = ContentTypeUtils.guessUploadFileContentType(cleanedFileName, uploadKind)
            val resolvedContentType = (
                if (contentType.isNullOrBlank() || contentType == "application/octet-stream") {
                    guessed
                } else if (ContentTypeUtils.testContentType(contentType, uploadKind)) {
                    contentType
                } else {
                    logger.warn("不支持的文件类型: {}", contentType)
                    guessed
                }
            ) ?: throw IllegalArgumentException("无法识别的文件类型")

            val objectKey = objectKey(uploadKind, cleanedFileName, principal)
            logger.debug("S3 object key: {}", objectKey)
            PreparedStorageUpload(
                objectKey = objectKey,
                contentType = resolvedContentType,
                contentLength = contentLength,
                finalUrl = finalUrlForKey(objectKey),
            )
        }
    }

    override fun uploadFileStream(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String?,
        contentLength: Long,
        inputStream: InputStream,
    ): Result<String> {
        val prepared = prepareUpload(uploadKind, fileName, principal, contentType, contentLength)
            .getOrElse { return Result.failure(it) }
        return uploadPreparedFileStream(prepared, inputStream)
    }

    override fun uploadPreparedFileStream(
        prepared: PreparedStorageUpload,
        inputStream: InputStream,
    ): Result<String> = runCatching {
        val request = PutObjectRequest.builder()
            .bucket(properties.requiredBucket())
            .key(normalizeKey(prepared.objectKey))
            .contentType(prepared.contentType)
            .contentLength(prepared.contentLength)
            .build()
        s3.putObject(request, RequestBody.fromInputStream(inputStream, prepared.contentLength))
        prepared.finalUrl
    }.onFailure {
        logger.error("上传文件到 S3 时发生错误: {}", it.message, it)
    }

    override fun finalUrlForKey(objectKey: String): String {
        val encodedKey = normalizeKey(objectKey).split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
        }
        return "${properties.requiredPublicBaseUrl()}/$encodedKey"
    }

    override fun deleteByUrl(fileUrl: String): StorageDeleteResult {
        val key = managedObjectKey(fileUrl) ?: return StorageDeleteResult.NOT_MANAGED
        return try {
            s3.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(properties.requiredBucket())
                    .key(key)
                    .build()
            )
            StorageDeleteResult.DELETED
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                StorageDeleteResult.DELETED
            } else {
                logger.error("删除 S3 文件时发生错误: {}", e.message, e)
                StorageDeleteResult.FAILED
            }
        } catch (e: Exception) {
            logger.error("删除 S3 文件时发生错误: {}", e.message, e)
            StorageDeleteResult.FAILED
        }
    }

    private fun objectKey(uploadKind: UploadFileKind, fileName: String, principal: OAuth2User): String {
        return when (uploadKind) {
            UploadFileKind.WORLDMAP -> {
                val title = fileName.substringAfter(']').trim()
                val category = when (val firstChar = title.firstOrNull()) {
                    null -> "Others"
                    else -> when {
                        firstChar.isLetter() -> firstChar.uppercaseChar().toString()
                        firstChar.isDigit() -> "0-9"
                        else -> "Others"
                    }
                }
                "${normalizePrefix(properties.worldmapPrefix)}/$category/$fileName"
            }

            UploadFileKind.PICBED -> {
                val userId = principal.getAttribute<Any>("id")
                    ?: throw IllegalArgumentException("无法获取上传者 ID")
                "${normalizePrefix(properties.picbedPrefix)}/picbed_$userId/$fileName"
            }
        }
    }

    private fun managedObjectKey(fileUrl: String): String? {
        return try {
            val base = URI(properties.requiredPublicBaseUrl())
            val target = URI(fileUrl)
            if (!base.scheme.equals(target.scheme, ignoreCase = true) ||
                !base.host.equals(target.host, ignoreCase = true) ||
                effectivePort(base) != effectivePort(target)
            ) {
                return null
            }

            val basePath = base.rawPath.trimEnd('/')
            val keyPrefix = "$basePath/"
            if (!target.rawPath.startsWith(keyPrefix)) return null
            val rawKey = target.rawPath.removePrefix(keyPrefix)
            if (rawKey.isBlank()) return null
            normalizeKey(
                rawKey.split('/').joinToString("/") {
                    URLDecoder.decode(it, StandardCharsets.UTF_8)
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun cleanFileName(fileName: String): String {
        val cleaned = fileName.trim()
        if (cleaned.isEmpty() || cleaned.contains('/') || cleaned.contains('\\') || cleaned.contains('\u0000')) {
            throw IllegalArgumentException("文件名不正确")
        }
        return cleaned
    }

    private fun normalizePrefix(prefix: String): String = normalizeKey(prefix).takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("S3 object prefix must not be empty")

    private fun normalizeKey(key: String): String = key.trim().trim('/')
}
