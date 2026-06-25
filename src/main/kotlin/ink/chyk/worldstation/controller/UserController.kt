package ink.chyk.worldstation.controller

import ink.chyk.worldstation.configuration.AdminConfig
import ink.chyk.worldstation.dto.UserDTO
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/user")
class UserController(
    private val adminConfig: AdminConfig
) {
    @GetMapping()
    fun user(@AuthenticationPrincipal principal: OAuth2User): UserDTO {
        val id = principal.attributes["id"] as Int
        return UserDTO(
            id = id,
            username = principal.attributes["username"] as String,
            nickname = principal.attributes["nickname"] as String,
            avatar_url = principal.attributes["avatar_url"] as String,
            isAdmin = id in adminConfig.ids
        )
    }
}
