package ink.chyk.worldstation.controller

import ink.chyk.worldstation.auth.AuthCookieService
import ink.chyk.worldstation.auth.FlarumAuthService
import ink.chyk.worldstation.auth.JwtService
import ink.chyk.worldstation.configuration.AdminConfig
import ink.chyk.worldstation.dto.ApiResponseDTO
import ink.chyk.worldstation.dto.LoginRequestDTO
import ink.chyk.worldstation.dto.UserDTO
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: FlarumAuthService,
    private val jwtService: JwtService,
    private val authCookieService: AuthCookieService,
    private val adminConfig: AdminConfig,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequestDTO,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<ApiResponseDTO<UserDTO>> {
        val identification = request.identification.trim()
        if (identification.isEmpty() || request.password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO(code = 400, message = "请输入用户名/邮箱和密码"))
        }

        return when (val result = authService.login(identification, request.password, httpRequest.remoteAddr)) {
            is FlarumAuthService.Result.Success -> {
                val principal = result.principal
                authCookieService.write(httpResponse, jwtService.issue(principal))
                ResponseEntity.ok(
                    ApiResponseDTO(
                        data = UserDTO(
                            id = principal.id,
                            username = principal.username,
                            nickname = principal.displayName,
                            avatar_url = principal.avatarUrl,
                            isAdmin = principal.id in adminConfig.ids,
                        )
                    )
                )
            }
            FlarumAuthService.Result.InvalidCredentials ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDTO(code = 401, message = "用户名或密码错误"))
            FlarumAuthService.Result.RateLimited ->
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponseDTO(code = 429, message = "尝试次数过多，请稍后再试"))
            FlarumAuthService.Result.Unavailable ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponseDTO(code = 503, message = "社区服务暂时不可用，请稍后重试"))
        }
    }

    @GetMapping("/csrf")
    fun csrf(token: CsrfToken): ApiResponseDTO<String> {
        return ApiResponseDTO(data = token.token)
    }
}
