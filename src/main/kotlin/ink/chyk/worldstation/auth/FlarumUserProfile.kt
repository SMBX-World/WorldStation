package ink.chyk.worldstation.auth

data class FlarumUserProfile(
    val id: Int,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
)
