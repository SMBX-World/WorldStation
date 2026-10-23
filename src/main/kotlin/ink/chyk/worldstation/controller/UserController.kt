package ink.chyk.worldstation.controller

import ink.chyk.worldstation.auth.FlarumPrincipal
import ink.chyk.worldstation.configuration.AdminConfig
import ink.chyk.worldstation.dto.UserDTO
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/user")
class UserController(
    private val adminConfig: AdminConfig
) {
    @GetMapping()
    fun user(@AuthenticationPrincipal principal: FlarumPrincipal): UserDTO {
        return UserDTO(
            id = principal.id,
            username = principal.username,
            nickname = principal.displayName,
            avatar_url = principal.avatarUrl,
            isAdmin = principal.id in adminConfig.ids
        )
    }
}
