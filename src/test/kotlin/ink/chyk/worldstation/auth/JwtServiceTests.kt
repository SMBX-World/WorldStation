package ink.chyk.worldstation.auth

import ink.chyk.worldstation.configuration.AuthProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class JwtServiceTests {
    companion object {
        private const val SECRET = "0123456789abcdef0123456789abcdef"
        private const val OTHER_SECRET = "fedcba9876543210fedcba9876543210"
    }

    private val principal = FlarumPrincipal(42, "Alice", "Alice Zhang", "https://forum.test/avatar.png")

    private fun service(secret: String = SECRET): JwtService {
        return JwtService(AuthProperties().apply {
            jwtSecret = secret
            sessionTtl = Duration.ofDays(90)
        })
    }

    @Test
    fun `issued token can be parsed`() {
        val service = service()
        val parsed = service.parse(service.issue(principal))
        assertEquals(principal, parsed)
    }

    @Test
    fun `token without avatar parses avatar as null`() {
        val service = service()
        val parsed = service.parse(service.issue(principal.copy(avatarUrl = null)))
        assertEquals(principal.copy(avatarUrl = null), parsed)
    }

    @Test
    fun `tampered token is rejected`() {
        val service = service()
        val token = service.issue(principal)
        assertNull(service.parse(token.dropLast(2) + "AA"))
    }

    @Test
    fun `expired token is rejected`() {
        val service = service()
        val token = service.issue(principal, Instant.now().minus(Duration.ofDays(91)))
        assertNull(service.parse(token))
    }

    @Test
    fun `token signed with another secret is rejected`() {
        val token = service().issue(principal)
        assertNull(service(OTHER_SECRET).parse(token))
    }
}
