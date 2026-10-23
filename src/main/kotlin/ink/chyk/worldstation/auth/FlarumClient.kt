package ink.chyk.worldstation.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import ink.chyk.worldstation.configuration.FlarumProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI

/**
 * Flarum REST API 客户端。Flarum token 仅在单次调用内使用，不会保存或写入日志。
 */
@Component
class FlarumClient(
    private val properties: FlarumProperties,
    private val restClient: RestClient,
) {
    sealed interface AuthResult {
        data class Success(val token: String, val userId: Int) : AuthResult
        data object InvalidCredentials : AuthResult
        data object RateLimited : AuthResult
        data object Unavailable : AuthResult
    }

    fun authenticate(identification: String, password: String): AuthResult {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("identification", identification)
            add("password", password)
        }

        return try {
            val response = restClient.post()
                .uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(FlarumTokenResponse::class.java)

            val token = response?.token
            val userId = response?.userId
            if (token.isNullOrBlank() || userId == null || userId <= 0) {
                AuthResult.InvalidCredentials
            } else {
                AuthResult.Success(token, userId)
            }
        } catch (_: HttpClientErrorException.Unauthorized) {
            AuthResult.InvalidCredentials
        } catch (_: HttpClientErrorException.TooManyRequests) {
            AuthResult.RateLimited
        } catch (_: HttpClientErrorException) {
            AuthResult.InvalidCredentials
        } catch (_: HttpServerErrorException) {
            AuthResult.Unavailable
        } catch (_: ResourceAccessException) {
            AuthResult.Unavailable
        } catch (_: RestClientException) {
            AuthResult.Unavailable
        }
    }

    fun fetchUser(userId: Int, token: String): FlarumUserProfile? {
        return try {
            val response = restClient.get()
                .uri("/api/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Token $token")
                .retrieve()
                .body(FlarumUserResponse::class.java)

            val attributes = response?.data?.attributes ?: return null
            val username = attributes.username?.takeIf { it.isNotBlank() } ?: return null
            FlarumUserProfile(
                id = userId,
                username = username,
                displayName = attributes.displayName?.takeIf { it.isNotBlank() } ?: username,
                avatarUrl = resolveAvatar(attributes.avatarUrl),
            )
        } catch (_: RestClientException) {
            null
        }
    }

    private fun resolveAvatar(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value
        }
        if (value.startsWith("//")) {
            return "https:$value"
        }
        val base = (properties.avatarBaseUrl?.takeIf { it.isNotBlank() } ?: properties.baseUrl)
            .trimEnd('/') + "/"
        return URI.create(base).resolve(value).toString()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FlarumTokenResponse(
    @JsonProperty("token") val token: String? = null,
    @JsonProperty("userId") val userId: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FlarumUserResponse(
    @JsonProperty("data") val data: FlarumUserData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FlarumUserData(
    @JsonProperty("attributes") val attributes: FlarumUserAttributes? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FlarumUserAttributes(
    @JsonProperty("username") val username: String? = null,
    @JsonProperty("displayName") val displayName: String? = null,
    @JsonProperty("avatarUrl") val avatarUrl: String? = null,
)
