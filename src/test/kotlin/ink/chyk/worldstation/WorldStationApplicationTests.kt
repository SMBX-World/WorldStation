package ink.chyk.worldstation

import ink.chyk.worldstation.configuration.S3StorageConfig
import ink.chyk.worldstation.configuration.S3StorageProperties
import org.junit.jupiter.api.Test

class WorldStationApplicationTests {
    @Test
    fun s3ClientConfigurationLoads() {
        val properties = S3StorageProperties().apply {
            endpoint = "http://localhost:9000"
            region = "us-east-1"
            bucket = "worldstation-test"
            publicBaseUrl = "http://localhost:9000/worldstation-test"
            pathStyleAccess = true
            accessKey = "test"
            secretKey = "test-secret"
        }
        S3StorageConfig(properties).s3Client().close()
    }
}
