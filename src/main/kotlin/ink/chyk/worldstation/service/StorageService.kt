package ink.chyk.worldstation.service

import ink.chyk.worldstation.enum.UploadFileKind
import org.springframework.security.oauth2.core.user.OAuth2User
import java.io.InputStream

data class PreparedStorageUpload(
    val objectKey: String,
    val contentType: String,
    val contentLength: Long,
    val finalUrl: String,
)

enum class StorageDeleteResult {
    DELETED,
    NOT_MANAGED,
    FAILED,
}

interface StorageService {
    fun prepareUpload(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String?,
        contentLength: Long,
    ): Result<PreparedStorageUpload>

    fun uploadFileStream(
        uploadKind: UploadFileKind,
        fileName: String,
        principal: OAuth2User,
        contentType: String?,
        contentLength: Long,
        inputStream: InputStream,
    ): Result<String>

    fun uploadPreparedFileStream(prepared: PreparedStorageUpload, inputStream: InputStream): Result<String>

    fun finalUrlForKey(objectKey: String): String

    fun deleteByUrl(fileUrl: String): StorageDeleteResult
}
