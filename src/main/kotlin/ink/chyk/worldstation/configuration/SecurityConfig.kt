package ink.chyk.worldstation.configuration

import ink.chyk.worldstation.auth.AuthCookieService
import ink.chyk.worldstation.auth.JsonAccessDeniedHandler
import ink.chyk.worldstation.auth.JsonAuthenticationEntryPoint
import ink.chyk.worldstation.auth.JwtAuthenticationFilter
import ink.chyk.worldstation.auth.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val authCookieService: AuthCookieService,
    private val jsonAuthenticationEntryPoint: JsonAuthenticationEntryPoint,
    private val jsonAccessDeniedHandler: JsonAccessDeniedHandler,
) {
    companion object {
        val DEBUG_DISABLE_WEB_SECURITY = false
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // 修复 Spring Security 6 中，默认不提供 CSRF 令牌的问题
        val requestHandler = CsrfTokenRequestAttributeHandler()
        requestHandler.setCsrfRequestAttributeName(null)

        http {
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = requestHandler
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                val authenticated2 = if (DEBUG_DISABLE_WEB_SECURITY) {
                    // 允许所有请求，方便测试
                    permitAll
                } else {
                    authenticated
                }
                authorize("/", permitAll)
                authorize("/404", permitAll)
                authorize("/index.html", permitAll)
                authorize("/sw.js", permitAll)
                authorize("/static-*.bundle", permitAll)
                authorize("/static/**", permitAll)
                authorize("/assets/**", permitAll)
                authorize("/upload/**", permitAll)
                authorize("/edit", permitAll)
                authorize("/api/auth/login", permitAll)
                authorize("/api/auth/csrf", permitAll)
                authorize(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/worldmaps"), permitAll)
                authorize(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/versions"), permitAll)
                authorize("/api/motd", permitAll)
                authorize("/docs/**", permitAll)
                authorize(anyRequest, authenticated2)
            }
            exceptionHandling {
                authenticationEntryPoint = jsonAuthenticationEntryPoint
                accessDeniedHandler = jsonAccessDeniedHandler
            }
            logout {
                logoutUrl = "/api/logout"
                addLogoutHandler(LogoutHandler { _, response, _ -> authCookieService.clear(response) })
                logoutSuccessHandler = HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)
            }
        }
        http.addFilterAfter(
            JwtAuthenticationFilter(jwtService, authCookieService),
            SecurityContextHolderFilter::class.java,
        )
        return http.build()
    }
}
