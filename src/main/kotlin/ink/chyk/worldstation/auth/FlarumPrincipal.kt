package ink.chyk.worldstation.auth

/**
 * 已通过 Flarum 认证的当前用户。
 * uid 与 Flarum 用户 ID 一致，用于地图归属与管理员判断。
 */
data class FlarumPrincipal(
    val id: Int,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
) : java.security.Principal {
    override fun getName(): String = username
}
