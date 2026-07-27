package ink.chyk.worldstation.service

import ink.chyk.worldstation.configuration.S3StorageProperties
import ink.chyk.worldstation.enum.UploadFileKind
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.io.ByteArrayInputStream

class S3StorageServiceTests {
    private val properties = S3StorageProperties().apply {
        endpoint = "https://s3.example.com"
        region = "auto"
        bucket = "worldstation"
        publicBaseUrl = "https://assets.example.com/files"
        worldmapPrefix = "/station/"
        picbedPrefix = "picbed"
    }
    private val s3 = mock(S3Client::class.java)
    private val service = S3StorageService(properties, s3)
    private val principal = DefaultOAuth2User(
        listOf(SimpleGrantedAuthority("ROLE_USER")),
        mapOf("id" to 42, "username" to "tester"),
        "username",
    )

    @Test
    fun `prepare upload builds categorized and encoded worldmap URL`() {
        val prepared = service.prepareUpload(
            UploadFileKind.WORLDMAP,
            "[SMBX2] 测试 map.zip",
            principal,
            "application/octet-stream",
            123,
        ).getOrThrow()

        assertEquals("station/测/[SMBX2] 测试 map.zip", prepared.objectKey)
        assertEquals("application/zip", prepared.contentType)
        assertEquals(
            "https://assets.example.com/files/station/%E6%B5%8B/%5BSMBX2%5D%20%E6%B5%8B%E8%AF%95%20map.zip",
            prepared.finalUrl,
        )
    }

    @Test
    fun `prepare upload puts picbed object below user directory`() {
        val prepared = service.prepareUpload(
            UploadFileKind.PICBED,
            "image.png",
            principal,
            "image/png",
            123,
        ).getOrThrow()

        assertEquals("picbed/picbed_42/image.png", prepared.objectKey)
    }

    @Test
    fun `prepare upload rejects unsafe filenames`() {
        val result = service.prepareUpload(
            UploadFileKind.PICBED,
            "../image.png",
            principal,
            "image/png",
            123,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `upload sends metadata and exact bytes to S3`() {
        `when`(s3.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().eTag("etag").build())
        val body = "world data".toByteArray()
        val prepared = PreparedStorageUpload(
            objectKey = "/station/W/world.zip",
            contentType = "application/zip",
            contentLength = body.size.toLong(),
            finalUrl = "https://assets.example.com/files/station/W/world.zip",
        )

        val result = service.uploadPreparedFileStream(prepared, ByteArrayInputStream(body))

        assertEquals(prepared.finalUrl, result.getOrThrow())
        val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        val bodyCaptor = ArgumentCaptor.forClass(RequestBody::class.java)
        verify(s3).putObject(requestCaptor.capture(), bodyCaptor.capture())
        assertEquals("worldstation", requestCaptor.value.bucket())
        assertEquals("station/W/world.zip", requestCaptor.value.key())
        assertEquals("application/zip", requestCaptor.value.contentType())
        assertEquals(body.size.toLong(), requestCaptor.value.contentLength())
        assertArrayEquals(body, bodyCaptor.value.contentStreamProvider().newStream().readBytes())
    }

    @Test
    fun `delete decodes a managed URL key`() {
        `when`(s3.deleteObject(any(DeleteObjectRequest::class.java)))
            .thenReturn(DeleteObjectResponse.builder().build())

        val result = service.deleteByUrl(
            "https://assets.example.com/files/station/%E6%B5%8B/a%20map.zip"
        )

        assertEquals(StorageDeleteResult.DELETED, result)
        val captor = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)
        verify(s3).deleteObject(captor.capture())
        assertEquals("station/测/a map.zip", captor.value.key())
    }

    @Test
    fun `delete skips legacy and lookalike URLs`() {
        assertEquals(
            StorageDeleteResult.NOT_MANAGED,
            service.deleteByUrl("https://legacy-storage.example.com/d/station/a.zip"),
        )
        assertEquals(
            StorageDeleteResult.NOT_MANAGED,
            service.deleteByUrl("https://assets.example.com/files-other/station/a.zip"),
        )
        verifyNoInteractions(s3)
    }
}
