package ink.chyk.worldstation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "worldstation.auth")
class AuthProperties {
    /** JWT 签名密钥，至少 32 字节；生产环境必须通过 AUTH_JWT_SECRET 注入 */
    var jwtSecret: String = ""

    /** 登录态有效期（JWT 与 Cookie 共用） */
    var sessionTtl: Duration = Duration.ofDays(90)

    /** 登录态 Cookie 名称 */
    var cookieName: String = "ws_auth"

    /** 是否只在 HTTPS 下发送 Cookie；本地 HTTP 开发需设为 false */
    var cookieSecure: Boolean = true

    /** Cookie SameSite 属性：Lax、Strict 或 None */
    var cookieSameSite: String = "Lax"

    /** 限流窗口内允许的最大登录失败次数 */
    var maxLoginFailures: Int = 10

    /** 登录失败计数窗口 */
    var failureWindow: Duration = Duration.ofMinutes(10)
}
