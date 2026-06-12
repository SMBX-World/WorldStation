package ink.chyk.worldstation.enum

enum class ChunkedUploadStatus {
    CREATED,
    RECEIVING,
    PROCESSING,
    CLIENT_UPLOAD_COMPLETED,
    COMPLETED,
    FAILED,
    ABORTED,
    EXPIRED
}
