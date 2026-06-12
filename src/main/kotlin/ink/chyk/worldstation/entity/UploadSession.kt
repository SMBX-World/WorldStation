package ink.chyk.worldstation.entity

import ink.chyk.worldstation.enum.ChunkedUploadStatus
import ink.chyk.worldstation.enum.UploadFileKind
import org.jetbrains.exposed.v1.core.Table

object UploadSession : Table("upload_sessions") {
    val id = uuid("id")
    val userId = integer("user_id")
    val uploadKind = enumerationByName("upload_kind", 32, UploadFileKind::class)
    val fileName = text("file_name")
    val contentType = text("content_type")
    val totalSize = long("total_size")
    val chunkSize = integer("chunk_size")
    val totalChunks = integer("total_chunks")
    val targetPath = text("target_path")
    val tempDir = text("temp_dir")
    val status = enumerationByName("status", 32, ChunkedUploadStatus::class)
    val savedChunkCount = integer("saved_chunk_count")
    val finalUrl = text("final_url").nullable()
    val error = text("error").nullable()
    val createdAtEpochMs = long("created_at_epoch_ms")
    val updatedAtEpochMs = long("updated_at_epoch_ms")
    val expiresAtEpochMs = long("expires_at_epoch_ms")

    override val primaryKey = PrimaryKey(id)
}
