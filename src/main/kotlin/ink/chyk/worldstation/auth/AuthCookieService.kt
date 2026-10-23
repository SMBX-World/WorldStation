package ink.chyk.worldstation.auth

import ink.chyk.worldstation.configuration.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service

@Service
class AuthCookieService(private val properties: AuthProperties) {
    private val sameSite = properties.cookieSameSite.trim().lowercase().replaceFirstChar { it.uppercase() }

    init {
        require(sameSite == "Lax" || sameSite == "Strict" || sameSite == "None") {
            "worldstation.auth.cookie-same-site 只能是 Lax、Strict 或 None"
        }
        if (sameSite == "None") {
            require(properties.cookieSecure) {
                "SameSite=None 时必须启用 worldstation.auth.cookie-secure"
            }
        }
    }

    fun write(response: HttpServletResponse, token: String) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, properties.sessionTtl.toSeconds()).toString())
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString())
    }

    fun read(request: HttpServletRequest): String? {
        return request.cookies
            ?.firstOrNull { it.name == properties.cookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildCookie(value: String, maxAgeSeconds: Long): ResponseCookie {
        return ResponseCookie.from(properties.cookieName, value)
            .httpOnly(true)
            .secure(properties.cookieSecure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(maxAgeSeconds)
            .build()
    }
}
