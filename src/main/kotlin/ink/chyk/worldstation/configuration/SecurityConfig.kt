package ink.chyk.worldstation.configuration

import org.springframework.context.annotation.*
import org.springframework.http.HttpMethod
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.config.annotation.web.builders.*
import org.springframework.security.config.annotation.web.configuration.*
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher



@Configuration
@EnableWebSecurity
class SecurityConfig {
    companion object {
        val DEBUG_DISABLE_WEB_SECURITY = false
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        clientRegistrationRepository: ObjectProvider<ClientRegistrationRepository>
    ): SecurityFilterChain {
        // fixes from https://stackoverflow.com/questions/74447118/csrf-protection-not-working-with-spring-security-6

        // 修复 Spring Security 6 中，默认不提供 CSRF 令牌的问题
        val requestHandler = CsrfTokenRequestAttributeHandler()
        requestHandler.setCsrfRequestAttributeName(null)

        http {
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = requestHandler
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
                authorize("/static-cc9fff6d.bundle", permitAll)
                authorize("/static/**", permitAll)
                authorize("/assets/**", permitAll)
                authorize(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/worldmaps"), permitAll)
                authorize(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/versions"), permitAll)
                authorize("/api/motd", permitAll)
                authorize("/login/**", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/docs/**", permitAll)
               authorize(anyRequest, authenticated2) // 需要认证的请求
            }
            if (!DEBUG_DISABLE_WEB_SECURITY && clientRegistrationRepository.getIfAvailable() != null) {
                oauth2Login {
                    defaultSuccessUrl("/", true)
                }
            }
        }
        return http.build()
    }
}
