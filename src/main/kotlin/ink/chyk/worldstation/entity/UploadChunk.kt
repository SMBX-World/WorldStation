package ink.chyk.worldstation.entity

import org.jetbrains.exposed.v1.core.Table

object UploadChunk : Table("upload_chunks") {
    val uploadId = uuid("upload_id").references(UploadSession.id)
    val chunkIndex = integer("chunk_index")
    val size = integer("size")
    val sha256 = varchar("sha256", 64)
    val receivedAtEpochMs = long("received_at_epoch_ms")
    val savedAtEpochMs = long("saved_at_epoch_ms").nullable()

    override val primaryKey = PrimaryKey(uploadId, chunkIndex)
}
