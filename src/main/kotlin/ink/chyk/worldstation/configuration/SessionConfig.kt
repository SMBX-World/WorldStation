package ink.chyk.worldstation.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.FlushMode
import org.springframework.session.SaveMode
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer

/**
 * Spring Session 配置。
 *
 * 通过将 HTTP 会话外部化到 Redis，实现：
 * 1. 应用镜像更新部署时会话不丢失
 * 2. 容器实例重启/重建时用户无需重新登录
 * 3. 服务进程异常终止后自动恢复会话状态
 *
 * 关键设计：
 * - FlushMode.IMMEDIATE：每次请求后立即将会话变更持久化到 Redis，确保即使进程意外终止也不丢失
 * - SaveMode.ON_SET_ATTRIBUTE：仅在属性实际变更时才保存，减少 Redis 写入压力
 * - Cookie 序列化器：统一域名、路径、sameSite 策略
 * - 序列化器使用 Spring Boot 默认的 GenericJackson2JsonRedisSerializer，已支持 OAuth2 principal
 */
@Configuration
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = 604800,  // 7 天无活动后会话过期
    flushMode = FlushMode.IMMEDIATE,         // 每次请求后立即写入 Redis
    saveMode = SaveMode.ON_SET_ATTRIBUTE,    // 仅在属性变更时保存
    redisNamespace = "worldstation:session"  // Redis key 命名空间前缀
)
class SessionConfig {

    /**
     * Cookie 序列化器。
     * 确保 JSESSIONID cookie 在各环境下的行为一致。
     */
    @Bean
    fun cookieSerializer(): CookieSerializer {
        val serializer = DefaultCookieSerializer()
        serializer.setCookieName("JSESSIONID")
        serializer.setCookiePath("/")
        serializer.setUseHttpOnlyCookie(true)
        serializer.setUseSecureCookie(false) // 由反向代理处理 HTTPS
        serializer.setSameSite("Lax")
        // 不设置 domain，由浏览器自动使用当前域名
        return serializer
    }
}
