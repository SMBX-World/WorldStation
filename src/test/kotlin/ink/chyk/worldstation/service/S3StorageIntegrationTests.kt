package ink.chyk.worldstation.service

import ink.chyk.worldstation.configuration.S3StorageConfig
import ink.chyk.worldstation.configuration.S3StorageProperties
import ink.chyk.worldstation.enum.UploadFileKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.ByteArrayInputStream

class KGenericContainer(imageName: DockerImageName) : GenericContainer<KGenericContainer>(imageName)

@Testcontainers(disabledWithoutDocker = true)
class S3StorageIntegrationTests {
    companion object {
        private const val ACCESS_KEY = "worldstation-test"
        private const val SECRET_KEY = "worldstation-test-secret"
        private const val BUCKET = "worldstation"

        @Container
        @JvmStatic
        val minio = KGenericContainer(
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z")
        )
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
    }

    private lateinit var client: S3Client
    private lateinit var service: S3StorageService

    @BeforeEach
    fun setUp() {
        val endpoint = "http://${minio.host}:${minio.getMappedPort(9000)}"
        val properties = S3StorageProperties().apply {
            this.endpoint = endpoint
            region = "us-east-1"
            bucket = BUCKET
            publicBaseUrl = "$endpoint/$BUCKET"
            pathStyleAccess = true
            accessKey = ACCESS_KEY
            secretKey = SECRET_KEY
        }
        client = S3StorageConfig(properties).s3Client()
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build())
        service = S3StorageService(properties, client)
    }

    @AfterEach
    fun tearDown() {
        client.close()
    }

    @Test
    fun `upload and delete object against MinIO`() {
        val principal = DefaultOAuth2User(
            listOf(SimpleGrantedAuthority("ROLE_USER")),
            mapOf("id" to 7, "username" to "tester"),
            "username",
        )
        val content = "integration test".toByteArray()
        val prepared = service.prepareUpload(
            UploadFileKind.PICBED,
            "test image.png",
            principal,
            "image/png",
            content.size.toLong(),
        ).getOrThrow()

        service.uploadPreparedFileStream(prepared, ByteArrayInputStream(content)).getOrThrow()
        val stored = client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(BUCKET).key(prepared.objectKey).build()
        ).asByteArray()
        assertArrayEquals(content, stored)

        assertEquals(StorageDeleteResult.DELETED, service.deleteByUrl(prepared.finalUrl))
        assertEquals(null, client.listObjectsV2 { it.bucket(BUCKET) }.contents().firstOrNull())
    }
}
