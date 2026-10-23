package ink.chyk.worldstation.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class UserDTO(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatar_url: String? = null,
    @get:JsonProperty("isAdmin")
    val isAdmin: Boolean = false,
)
