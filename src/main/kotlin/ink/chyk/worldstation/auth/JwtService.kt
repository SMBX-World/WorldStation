package ink.chyk.worldstation.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import ink.chyk.worldstation.configuration.AuthProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Service
class JwtService(properties: AuthProperties) {
    companion object {
        private const val ISSUER = "worldstation"
        private const val MIN_SECRET_BYTES = 32
    }

    private val sessionTtl = properties.sessionTtl
    private val secretKey: SecretKey = createSecretKey(properties)
    private val encoder: JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))
    private val decoder: JwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
        .also { it.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER)) }

    init {
        require(sessionTtl.isPositive) {
            "worldstation.auth.session-ttl 必须大于 0"
        }
    }

    private fun createSecretKey(properties: AuthProperties): SecretKey {
        val bytes = properties.jwtSecret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) {
            "worldstation.auth.jwt-secret 至少需要 32 字节"
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    fun issue(principal: FlarumPrincipal, now: Instant = Instant.now()): String {
        val claimsBuilder = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(principal.id.toString())
            .issuedAt(now)
            .expiresAt(now.plus(sessionTtl))
            .claim("username", principal.username)
            .claim("nickname", principal.displayName)
        principal.avatarUrl?.let { claimsBuilder.claim("avatar", it) }

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claimsBuilder.build())).tokenValue
    }

    fun parse(token: String): FlarumPrincipal? {
        return try {
            val jwt = decoder.decode(token)
            val id = jwt.subject?.toIntOrNull() ?: return null
            val username = jwt.getClaimAsString("username")?.takeIf { it.isNotBlank() } ?: return null
            val nickname = jwt.getClaimAsString("nickname")?.takeIf { it.isNotBlank() } ?: username
            FlarumPrincipal(
                id = id,
                username = username,
                displayName = nickname,
                avatarUrl = jwt.getClaimAsString("avatar"),
            )
        } catch (_: JwtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
