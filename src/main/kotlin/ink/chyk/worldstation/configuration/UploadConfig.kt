package ink.chyk.worldstation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "worldstation.upload")
class UploadConfig {
    var tempDir: String = "./upload-tmp"
    var chunkSize: Int = 8 * 1024 * 1024
    var streamStartWindowChunks: Int = 4
    var chunkWaitTimeoutSeconds: Long = 60
    var sessionTtlHours: Long = 24
    var maxActiveSessionsPerUser: Int = 2
    var keepChunksUntilCompleted: Boolean = true
}
