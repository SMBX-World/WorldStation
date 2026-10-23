package ink.chyk.worldstation.auth

import org.springframework.stereotype.Service

@Service
class FlarumAuthService(
    private val flarumClient: FlarumClient,
    private val rateLimiter: LoginRateLimiter,
) {
    sealed interface Result {
        data class Success(val principal: FlarumPrincipal) : Result
        data object InvalidCredentials : Result
        data object RateLimited : Result
        data object Unavailable : Result
    }

    fun login(identification: String, password: String, remoteAddr: String): Result {
        val key = rateLimiter.key(remoteAddr, identification)
        if (rateLimiter.isBlocked(key)) {
            return Result.RateLimited
        }

        return when (val auth = flarumClient.authenticate(identification, password)) {
            is FlarumClient.AuthResult.Success -> {
                val profile = flarumClient.fetchUser(auth.userId, auth.token)
                if (profile == null) {
                    Result.Unavailable
                } else {
                    rateLimiter.reset(key)
                    Result.Success(
                        FlarumPrincipal(
                            id = profile.id,
                            username = profile.username,
                            displayName = profile.displayName,
                            avatarUrl = profile.avatarUrl,
                        )
                    )
                }
            }
            FlarumClient.AuthResult.InvalidCredentials -> {
                rateLimiter.recordFailure(key)
                Result.InvalidCredentials
            }
            FlarumClient.AuthResult.RateLimited -> Result.RateLimited
            FlarumClient.AuthResult.Unavailable -> Result.Unavailable
        }
    }
}
