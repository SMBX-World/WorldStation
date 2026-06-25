package ink.chyk.worldstation.dto

data class UserDTO(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatar_url: String? = null,
    val isAdmin: Boolean = false,
)
