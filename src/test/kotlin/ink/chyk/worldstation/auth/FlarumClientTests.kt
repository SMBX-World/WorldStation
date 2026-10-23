package ink.chyk.worldstation.auth

import ink.chyk.worldstation.configuration.FlarumProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class FlarumClientTests {
    private val properties = FlarumProperties().apply {
        baseUrl = "https://forum.test"
    }

    private fun buildClient(): Pair<FlarumClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        return FlarumClient(properties, builder.build()) to server
    }

    @Test
    fun `authenticate posts form data and returns token`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(content().string("identification=alice&password=secret"))
            .andRespond(
                withSuccess(
                    """{"token":"flarum-token","userId":42}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = client.authenticate("alice", "secret")

        assertEquals(FlarumClient.AuthResult.Success("flarum-token", 42), result)
        server.verify()
    }

    @Test
    fun `authenticate maps 401 to invalid credentials`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertEquals(FlarumClient.AuthResult.InvalidCredentials, client.authenticate("alice", "wrong"))
        server.verify()
    }

    @Test
    fun `authenticate maps 429 to rate limited`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/token"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertEquals(FlarumClient.AuthResult.RateLimited, client.authenticate("alice", "secret"))
        server.verify()
    }

    @Test
    fun `authenticate maps 5xx to unavailable`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/token"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertEquals(FlarumClient.AuthResult.Unavailable, client.authenticate("alice", "secret"))
        server.verify()
    }

    @Test
    fun `fetch user maps attributes and resolves relative avatar`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/users/42"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Token flarum-token"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "attributes": {
                          "username": "Alice",
                          "displayName": "",
                          "avatarUrl": "/assets/avatars/alice.png"
                        }
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )

        val profile = client.fetchUser(42, "flarum-token")

        assertEquals(
            FlarumUserProfile(
                id = 42,
                username = "Alice",
                displayName = "Alice",
                avatarUrl = "https://forum.test/assets/avatars/alice.png",
            ),
            profile,
        )
        server.verify()
    }

    @Test
    fun `fetch user returns null when username is missing`() {
        val (client, server) = buildClient()
        server.expect(requestTo("https://forum.test/api/users/42"))
            .andRespond(
                withSuccess(
                    """{"data":{"attributes":{"displayName":"Alice"}}}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        assertNull(client.fetchUser(42, "flarum-token"))
        server.verify()
    }
}
