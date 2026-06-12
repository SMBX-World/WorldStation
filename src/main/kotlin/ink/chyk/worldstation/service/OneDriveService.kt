package ink.chyk.worldstation.service

import ink.chyk.worldstation.configuration.OneDriveConfig
import ink.chyk.worldstation.enum.UploadFileKind
import ink.chyk.worldstation.util.ContentTypeUtils
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestTemplate
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

data class PreparedOneDriveUpload(
    val uploadPath: String,
    val parentPath: String,
    val contentType: String,
    val contentLength: Long,
    val finalUrl: String,
)

@Service
class OneDriveService(
    private val config: OneDriveConfig,
    private val client: RestTemplate
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OneDriveService::class.java)
        private val picbedLimit: Long = 30 * 1024 * 1024 // 图床上传文件大小限制为 30MB
    }

    private fun alistToken(): String = config.alistToken
        ?: throw IllegalArgumentException("Alist token is not configured")

    private fun alistUrl(): String = config.alist
        ?: throw IllegalArgumentException("Alist URL is not configured")

    fun getUploadFilePath(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User
    ): String {
        return when (uploadKind) {
            UploadFileKind.WORLDMAP -> {
                // 配置中设置的世界地图文件夹
                var path = config.worldmapPath ?: throw IllegalArgumentException("Worldmap path is not configured")

                // 根据标题的首字母分类存储
                val title = fileName.substringAfter(']').trim() // 去掉可能的前缀
                path += when (val firstChar = title.firstOrNull()) {
                    null -> "/Others"
                    else -> {
                        if (firstChar.isLetter()) {
                            "/${firstChar.uppercaseChar()}"
                        } else if (firstChar.isDigit()) {
                            "/0-9"
                        } else {
                            "/Others"
                        }
                    }
                }

                // 最终路径加上文件名
                path += "/$fileName"
                path
            }

            UploadFileKind.PICBED -> {
                // 配置中设置的图床文件夹
                var path = config.picbedPath ?: throw IllegalArgumentException("Picbed path is not configured")
                // 根据上传者的 ID 分类存储
                path += "/picbed_${principal.getAttribute<Any>("id")}/${fileName}"
                path
            }
        }
    }

    private fun alistHttpHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("Authorization", alistToken())
        return headers
    }

    private fun makeDirectory(path: String) {
        val headers = alistHttpHeaders()
        val request = HttpEntity("""{"path": "$path"}""", headers)
        client.postForEntity(
            "${alistUrl()}/api/fs/mkdir",
            request,
            String::class.java
        )
    }

    private fun refreshFileSystem(path: String) {
        // 刷新 alist 文件系统中某个文件夹的缓存
        val headers = alistHttpHeaders()
        val request = HttpEntity("""{"path": "$path", "refresh": true}""", headers)
        val resp = client.postForEntity(
            "${alistUrl()}/api/fs/list",
            request,
            String::class.java,
        )
        logger.debug("刷新文件系统响应: {}", resp.body)
    }

    private fun removeFile(path: String) {
        val trimmedPath = path.trim('/')
        var parent = trimmedPath.substringBeforeLast('/')
        if (!parent.startsWith('/')) parent = "/$parent"
        val filename = trimmedPath.substringAfterLast('/')
        val headers = alistHttpHeaders()
        val request = HttpEntity("""{"dir": "$parent", "names": ["$filename"]}""", headers)
        client.postForEntity(
            "${alistUrl()}/api/fs/remove",
            request,
            String::class.java
        )
    }

    fun limitPicbedContentLength(contentLength: Long): Boolean {
        // 限制图床类上传文件大小为 30MB
        return contentLength <= picbedLimit
    }

    fun finalUrlForPath(uploadPath: String): String = "${alistUrl()}/d$uploadPath"

    fun uploadFileStreamToOneDrive(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String,
        contentLength: Long,
        inputStream: InputStream
    ): Result<String> {
        val prepared = prepareUpload(uploadKind, fileName, principal, contentType, contentLength)
            .getOrElse { return Result.failure(it) }

        return uploadPreparedFileStream(prepared, inputStream)
    }

    fun prepareUpload(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String?,
        contentLength: Long,
    ): Result<PreparedOneDriveUpload> {
        val uploadPath = getUploadFilePath(uploadKind, fileName, principal)
        val parentPath = uploadPath.substringBeforeLast("/")
        logger.debug("文件路径: {}", uploadPath)

        val guessed = ContentTypeUtils.guessUploadFileContentType(fileName, uploadKind)
        val resolvedContentType = if (contentType == null || contentType == "application/octet-stream") {
            guessed
        } else {
            if (!ContentTypeUtils.testContentType(contentType, uploadKind)) {
                logger.warn("不支持的文件类型: {}", contentType)
                guessed
            } else contentType
        }
        logger.debug("Content-Type: {}", resolvedContentType)

        if (resolvedContentType == null) {
            return Result.failure(IllegalArgumentException("无法识别的文件类型"))
        }

        // 图床场景下需要检查文件大小并创建目录
        if (uploadKind == UploadFileKind.PICBED) {
            if (!limitPicbedContentLength(contentLength)) {
                return Result.failure(
                    FileSizeLimitExceededException("上传的文件大小超过限制", contentLength, picbedLimit)
                )
            }
            makeDirectory(parentPath) // 确保目录存在
        }

        return Result.success(
            PreparedOneDriveUpload(
                uploadPath = uploadPath,
                parentPath = parentPath,
                contentType = resolvedContentType,
                contentLength = contentLength,
                finalUrl = finalUrlForPath(uploadPath),
            )
        )
    }

    fun uploadPreparedFileStream(
        prepared: PreparedOneDriveUpload,
        inputStream: InputStream
    ): Result<String> = uploadPreparedStream(prepared) { outputStream ->
        inputStream.transferTo(outputStream)
    }

    fun uploadPreparedStream(
        prepared: PreparedOneDriveUpload,
        bodyWriter: (OutputStream) -> Unit
    ): Result<String> {
        val api = URI("${alistUrl()}/api/fs/put")

        val headers = alistHttpHeaders().apply {
            set("Content-Type", prepared.contentType)
            set("Content-Length", prepared.contentLength.toString())
            set("File-Path", prepared.uploadPath)
        }

        return try {
            // 执行流式转发
            client.execute<Any>(
                api,
                HttpMethod.PUT,
                { request ->
                    // 复制请求头
                    headers.forEach { (key, values) ->
                        values.forEach { value ->
                            request.headers.add(key, value)
                        }
                    }
                    // 设置请求体
                    bodyWriter(request.body)
                },
                { response ->
                    logger.debug("Response status is: {}", response.statusCode)
                    if (!response.statusCode.is2xxSuccessful) {
                        throw IllegalStateException("存储服务返回错误状态: ${response.statusCode}")
                    }
                }
            )
            // 刷新文件系统缓存
            refreshFileSystem(prepared.parentPath)
            Result.success(prepared.finalUrl)
        } catch (e: Exception) {
            logger.error("上传过程中发生错误: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun tryRemoveByUrl(fileUrl: String): Boolean {
        // extract path from URL
        val path = fileUrl.substringAfterLast(alistUrl()).substringAfter("/d")
        try {
            removeFile(path)
            return true
        } catch (e: Exception) {
            logger.error("删除文件时发生错误: ${e.message}", e)
            return false
        }
    }
}
