package ink.chyk.worldstation.dto

import ink.chyk.worldstation.enum.ChunkedUploadStatus
import ink.chyk.worldstation.enum.UploadFileKind
import java.util.UUID

data class CreateChunkedUploadRequestDTO(
    val uploadKind: UploadFileKind,
    val fileName: String,
    val contentType: String? = null,
    val totalSize: Long,
)

data class ChunkedUploadDTO(
    val uploadId: UUID,
    val chunkSize: Int,
    val totalChunks: Int,
    val receivedChunks: List<Int>,
    val savedChunkCount: Int,
    val status: ChunkedUploadStatus,
    val finalUrl: String? = null,
    val error: String? = null,
)

data class ChunkUploadDTO(
    val chunkIndex: Int,
    val received: Boolean,
    val status: ChunkedUploadStatus,
)
