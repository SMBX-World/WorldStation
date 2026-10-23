package ink.chyk.worldstation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "worldstation.flarum")
class FlarumProperties {
    /** Flarum 论坛根地址，例如 https://smbx.world */
    var baseUrl: String = "https://smbx.world"

    /** 连接 Flarum 的超时时间 */
    var connectTimeout: Duration = Duration.ofSeconds(5)

    /** 读取 Flarum 响应的超时时间 */
    var readTimeout: Duration = Duration.ofSeconds(10)

    /** 头像 CDN 基址；为空时使用 baseUrl */
    var avatarBaseUrl: String? = null
}
