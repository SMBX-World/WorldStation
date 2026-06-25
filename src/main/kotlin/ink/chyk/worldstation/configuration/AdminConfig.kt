package ink.chyk.worldstation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "worldstation.admin")
class AdminConfig {
    /** 管理员用户 ID 列表 */
    var ids: List<Int> = emptyList()
}
