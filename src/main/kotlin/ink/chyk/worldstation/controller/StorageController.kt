package ink.chyk.worldstation.controller

import ink.chyk.worldstation.dto.ApiResponseDTO
import ink.chyk.worldstation.dto.CreateChunkedUploadRequestDTO
import ink.chyk.worldstation.enum.ChunkedUploadStatus
import ink.chyk.worldstation.enum.UploadFileKind
import ink.chyk.worldstation.service.ChunkChecksumException
import ink.chyk.worldstation.service.ChunkConflictException
import ink.chyk.worldstation.service.ChunkedUploadException
import ink.chyk.worldstation.service.ChunkedUploadService
import ink.chyk.worldstation.service.StorageService
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.io.InputStream
import java.util.UUID

@RestController
@RequestMapping("/api/storage")
class StorageController(
    private val storageService: StorageService,
    private val chunkedUploadService: ChunkedUploadService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(StorageController::class.java)
    }

    @PutMapping("/upload")
    fun uploadFileStream(
        @RequestParam("upload_kind") uploadKind: UploadFileKind,
        @RequestParam("file_name") fileName: String,
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestHeader("Content-Type") contentType: String,
        @RequestHeader("Content-Length") contentLength: Long,
        inputStream: InputStream,
    ): ResponseEntity<ApiResponseDTO<Any>> {
        val uploadResult = storageService.uploadFileStream(
            uploadKind, fileName, principal, contentType, contentLength, inputStream
        )
        return if (uploadResult.isSuccess) {
            ResponseEntity.ok(ApiResponseDTO(data = uploadResult.getOrNull(), message = "文件上传成功"))
        } else {
            uploadFailure(uploadResult.exceptionOrNull())
        }
    }

    @PostMapping("/uploads")
    fun createChunkedUpload(
        @RequestBody request: CreateChunkedUploadRequestDTO,
        @AuthenticationPrincipal principal: OAuth2User,
    ): ResponseEntity<ApiResponseDTO<Any>> = handleChunkedUploadCall {
        ResponseEntity.ok(
            ApiResponseDTO(
                data = chunkedUploadService.createSession(request, principal),
                message = "上传会话已创建"
            )
        )
    }

    @PutMapping("/uploads/{uploadId}/chunks/{chunkIndex}")
    fun uploadChunk(
        @PathVariable uploadId: UUID,
        @PathVariable chunkIndex: Int,
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestHeader("Content-Length") contentLength: Long,
        @RequestHeader("Content-Range") contentRange: String,
        @RequestHeader("X-Chunk-SHA256") sha256: String,
        inputStream: InputStream,
    ): ResponseEntity<ApiResponseDTO<Any>> = handleChunkedUploadCall {
        ResponseEntity.ok(
            ApiResponseDTO(
                data = chunkedUploadService.uploadChunk(
                    uploadId, chunkIndex, contentLength, contentRange, sha256, inputStream, principal
                ),
                message = "分块上传成功"
            )
        )
    }

    @GetMapping("/uploads/{uploadId}")
    fun getChunkedUpload(
        @PathVariable uploadId: UUID,
        @AuthenticationPrincipal principal: OAuth2User,
    ): ResponseEntity<ApiResponseDTO<Any>> = handleChunkedUploadCall {
        ResponseEntity.ok(
            ApiResponseDTO(
                data = chunkedUploadService.getSessionDTO(uploadId, principal),
                message = "上传状态获取成功"
            )
        )
    }

    @PostMapping("/uploads/{uploadId}/complete")
    fun completeChunkedUpload(
        @PathVariable uploadId: UUID,
        @AuthenticationPrincipal principal: OAuth2User,
    ): ResponseEntity<ApiResponseDTO<Any>> = handleChunkedUploadCall {
        val dto = chunkedUploadService.completeUpload(uploadId, principal)
        val status = if (dto.status == ChunkedUploadStatus.COMPLETED) HttpStatus.OK else HttpStatus.ACCEPTED
        ResponseEntity.status(status).body(
            ApiResponseDTO(
                data = dto,
                message = if (dto.status == ChunkedUploadStatus.COMPLETED) {
                    "文件上传成功"
                } else {
                    "上传完成，服务器正在处理文件"
                }
            )
        )
    }

    @DeleteMapping("/uploads/{uploadId}")
    fun abortChunkedUpload(
        @PathVariable uploadId: UUID,
        @AuthenticationPrincipal principal: OAuth2User,
    ): ResponseEntity<ApiResponseDTO<Any>> = handleChunkedUploadCall {
        chunkedUploadService.abort(uploadId, principal)
        ResponseEntity.ok(ApiResponseDTO(message = "上传已取消"))
    }

    private fun uploadFailure(exception: Throwable?): ResponseEntity<ApiResponseDTO<Any>> {
        return when (exception) {
            is FileSizeLimitExceededException -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ApiResponseDTO(
                    code = 413,
                    message = "文件大小超过限制，请确保文件小于 ${exception.permittedSize} 字节"
                )
            )

            is IllegalArgumentException -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponseDTO(code = 400, message = exception.message ?: "上传请求不正确")
            )

            else -> {
                logger.error("上传文件失败: {}", exception?.message, exception)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO(code = 500, message = "文件上传失败，未知原因")
                )
            }
        }
    }

    private fun handleChunkedUploadCall(
        call: () -> ResponseEntity<ApiResponseDTO<Any>>
    ): ResponseEntity<ApiResponseDTO<Any>> {
        return try {
            call()
        } catch (e: FileSizeLimitExceededException) {
            ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ApiResponseDTO(
                    code = 413,
                    message = "文件大小超过限制，请确保文件小于 ${e.permittedSize} 字节"
                )
            )
        } catch (e: ChunkChecksumException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiResponseDTO(code = 422, message = e.message ?: "分块校验失败，请重传该分块")
            )
        } catch (e: ChunkConflictException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponseDTO(code = 409, message = e.message ?: "分块状态冲突")
            )
        } catch (e: ChunkedUploadException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponseDTO(code = 400, message = e.message ?: "上传请求不正确")
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponseDTO(code = 400, message = e.message ?: "上传请求不正确")
            )
        } catch (e: Exception) {
            logger.error("分块上传失败: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponseDTO(code = 500, message = "文件上传失败，未知原因")
            )
        }
    }
}
