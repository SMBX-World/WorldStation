package ink.chyk.worldstation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.SdkSystemSetting
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "worldstation.storage.s3")
class S3StorageProperties {
    var endpoint: String? = null
    var region: String? = null
    var bucket: String? = null
    var publicBaseUrl: String? = null
    var pathStyleAccess: Boolean = false
    var accessKey: String? = null
    var secretKey: String? = null
    var sessionToken: String? = null
    var worldmapPrefix: String = "station"
    var picbedPrefix: String = "picbed"

    fun requiredEndpoint(): URI = URI.create(required("endpoint", endpoint))
    fun requiredRegion(): String = required("region", region)
    fun requiredBucket(): String = required("bucket", bucket)
    fun requiredPublicBaseUrl(): String = required("public-base-url", publicBaseUrl).trimEnd('/')

    private fun required(name: String, value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("worldstation.storage.s3.$name is not configured")
}

@Configuration
class S3StorageConfig(private val properties: S3StorageProperties) {
    @Bean
    fun s3Client(): S3Client {
        // 新版 AWS SDK 默认给 PutObject 增加额外校验和；部分兼容实现不支持该扩展。
        // 仍允许部署方通过标准 AWS 环境变量显式覆盖此设置。
        val checksumSetting = SdkSystemSetting.AWS_REQUEST_CHECKSUM_CALCULATION
        if (System.getProperty(checksumSetting.property()).isNullOrBlank() &&
            System.getenv(checksumSetting.environmentVariable()).isNullOrBlank()
        ) {
            System.setProperty(checksumSetting.property(), "WHEN_REQUIRED")
        }
        return S3Client.builder()
            .endpointOverride(properties.requiredEndpoint())
            .region(Region.of(properties.requiredRegion()))
            .credentialsProvider(credentialsProvider())
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.pathStyleAccess)
                    .chunkedEncodingEnabled(false)
                    .build()
            )
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofHours(2))
                    .build()
            )
            .build()
    }

    private fun credentialsProvider(): AwsCredentialsProvider {
        val accessKey = properties.accessKey?.trim()?.takeIf { it.isNotEmpty() }
        val secretKey = properties.secretKey?.trim()?.takeIf { it.isNotEmpty() }
        val sessionToken = properties.sessionToken?.trim()?.takeIf { it.isNotEmpty() }

        if ((accessKey == null) != (secretKey == null)) {
            throw IllegalStateException("S3 access-key and secret-key must be configured together")
        }
        if (accessKey == null) {
            if (sessionToken != null) {
                throw IllegalStateException("S3 session-token requires access-key and secret-key")
            }
            return DefaultCredentialsProvider.builder().build()
        }

        val credentials = if (sessionToken == null) {
            AwsBasicCredentials.create(accessKey, secretKey)
        } else {
            AwsSessionCredentials.create(accessKey, secretKey!!, sessionToken)
        }
        return StaticCredentialsProvider.create(credentials)
    }
}
