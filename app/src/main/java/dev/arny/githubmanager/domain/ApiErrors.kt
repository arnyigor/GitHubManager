package dev.arny.githubmanager.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 403 mixes permissions, primary and secondary rate limits; the message must say which one. */
enum class ApiFailureKind { UNAUTHORIZED, FORBIDDEN, RATE_LIMIT, SECONDARY_RATE_LIMIT, NOT_FOUND, CONFLICT, UNPROCESSABLE, UNKNOWN }

fun classifyFailure(
    code: Int,
    rateLimitRemaining: Int? = null,
    retryAfterSeconds: Long? = null
): ApiFailureKind = when {
    code == 401 -> ApiFailureKind.UNAUTHORIZED
    code == 403 && rateLimitRemaining == 0 -> ApiFailureKind.RATE_LIMIT
    code == 403 && retryAfterSeconds != null -> ApiFailureKind.SECONDARY_RATE_LIMIT
    code == 403 -> ApiFailureKind.FORBIDDEN
    code == 404 -> ApiFailureKind.NOT_FOUND
    code == 409 -> ApiFailureKind.CONFLICT
    code == 422 -> ApiFailureKind.UNPROCESSABLE
    code == 429 -> ApiFailureKind.RATE_LIMIT
    else -> ApiFailureKind.UNKNOWN
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private fun waitLabel(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    return if (minutes <= 1) "меньше минуты" else "около $minutes мин"
}

/**
 * `resetAt` is the `X-RateLimit-Reset` epoch second, `now` the current epoch second.
 * The client never retries automatically: the message only tells the user when to try.
 */
fun failureMessage(
    code: Int,
    kind: ApiFailureKind,
    resetAt: Long? = null,
    retryAfterSeconds: Long? = null,
    now: Long = System.currentTimeMillis() / 1000
): String = when (kind) {
    ApiFailureKind.UNAUTHORIZED -> "Авторизация отклонена. Проверьте токен."
    ApiFailureKind.RATE_LIMIT -> {
        val wait = when {
            retryAfterSeconds != null -> "Повторите через ${waitLabel(retryAfterSeconds)}."
            resetAt != null && resetAt > now -> "Лимит восстановится в " +
                Instant.ofEpochSecond(resetAt).atZone(ZoneId.systemDefault()).format(TIME) + "."
            else -> "Повторите позже."
        }
        "Исчерпан лимит запросов GitHub. $wait"
    }
    ApiFailureKind.SECONDARY_RATE_LIMIT ->
        "GitHub временно ограничил запросы (вторичный лимит). Повторите через " +
            waitLabel(retryAfterSeconds ?: 60L) + "."
    ApiFailureKind.FORBIDDEN -> "Недостаточно прав или политика организации запрещает действие."
    ApiFailureKind.NOT_FOUND -> "Ресурс не найден либо нет доступа."
    ApiFailureKind.CONFLICT -> "Конфликт состояния репозитория."
    ApiFailureKind.UNPROCESSABLE -> "GitHub отклонил изменение."
    ApiFailureKind.UNKNOWN -> "Ошибка GitHub API (HTTP $code)."
}
