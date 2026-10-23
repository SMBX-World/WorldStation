package ink.chyk.worldstation.auth

import ink.chyk.worldstation.configuration.AuthProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内登录失败限流。单实例部署足够；多实例时每个实例独立计数。
 */
@Component
class LoginRateLimiter(private val properties: AuthProperties) {
    private data class Attempt(val failures: Int, val windowStart: Instant)

    private val attempts = ConcurrentHashMap<String, Attempt>()

    fun key(remoteAddr: String, identification: String): String {
        return "$remoteAddr|${identification.lowercase()}"
    }

    fun isBlocked(key: String): Boolean {
        val now = Instant.now()
        val entry = attempts[key] ?: return false
        if (isExpired(entry, now)) {
            attempts.remove(key, entry)
            return false
        }
        return entry.failures >= properties.maxLoginFailures
    }

    fun recordFailure(key: String) {
        val now = Instant.now()
        attempts.compute(key) { _, current ->
            if (current == null || isExpired(current, now)) {
                Attempt(1, now)
            } else {
                Attempt(current.failures + 1, current.windowStart)
            }
        }
        if (attempts.size > MAX_ENTRIES) {
            attempts.entries.removeIf { isExpired(it.value, now) }
        }
    }

    fun reset(key: String) {
        attempts.remove(key)
    }

    private fun isExpired(entry: Attempt, now: Instant): Boolean {
        return Duration.between(entry.windowStart, now) >= properties.failureWindow
    }

    companion object {
        private const val MAX_ENTRIES = 10_000
    }
}
