package ink.chyk.worldstation.controller

import ink.chyk.worldstation.auth.AuthCookieService
import ink.chyk.worldstation.auth.FlarumAuthService
import ink.chyk.worldstation.auth.FlarumPrincipal
import ink.chyk.worldstation.auth.JsonAccessDeniedHandler
import ink.chyk.worldstation.auth.JsonAuthenticationEntryPoint
import ink.chyk.worldstation.auth.JwtService
import ink.chyk.worldstation.configuration.AdminConfig
import ink.chyk.worldstation.configuration.AuthProperties
import ink.chyk.worldstation.configuration.SecurityConfig
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [AuthController::class, UserController::class])
@Import(
    SecurityConfig::class,
    JwtService::class,
    AuthCookieService::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@EnableConfigurationProperties(AuthProperties::class, AdminConfig::class)
@TestPropertySource(
    properties = [
        "worldstation.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "worldstation.auth.cookie-secure=false",
        "worldstation.auth.cookie-name=ws_auth",
        "worldstation.admin.ids=1,3,6",
    ]
)
class AuthControllerTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var authService: FlarumAuthService

    @Test
    fun `successful login sets http only cookie and returns user`() {
        Mockito.`when`(authService.login(anyString(), anyString(), anyString()))
            .thenReturn(
                FlarumAuthService.Result.Success(
                    FlarumPrincipal(42, "alice", "Alice", null)
                )
            )

        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identification":"alice","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").value(42))
            .andExpect(jsonPath("$.data.isAdmin").value(false))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ws_auth=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
    }

    @Test
    fun `invalid credentials return 401 without cookie`() {
        Mockito.`when`(authService.login(anyString(), anyString(), anyString()))
            .thenReturn(FlarumAuthService.Result.InvalidCredentials)

        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identification":"alice","password":"wrong"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `login without csrf token is forbidden`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identification":"alice","password":"secret"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `csrf endpoint returns token`() {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").isNotEmpty)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
    }

    @Test
    fun `user endpoint returns 401 json without cookie`() {
        mockMvc.perform(get("/api/user"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(401))
    }

    @Test
    fun `user endpoint authenticates jwt cookie and marks admin`() {
        val token = jwtService.issue(FlarumPrincipal(3, "admin", "Admin", null))

        mockMvc.perform(get("/api/user").cookie(Cookie("ws_auth", token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.isAdmin").value(true))
    }

    @Test
    fun `logout clears auth cookie`() {
        mockMvc.perform(post("/api/logout").with(csrf()))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ws_auth=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
    }
}
