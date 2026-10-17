package dev.arny.githubmanager.domain

import kotlinx.coroutines.CancellationException

enum class VisibilityFilter { ALL, PUBLIC, PRIVATE }

fun filterRepositories(
    items: List<GitHubRepository>,
    query: String,
    filter: VisibilityFilter
): List<GitHubRepository> {
    val normalized = query.trim()
    return items.filter { repo ->
        val matches = normalized.isEmpty() ||
            repo.fullName.contains(normalized, ignoreCase = true) ||
            (repo.description?.contains(normalized, ignoreCase = true) == true)
        matches && when (filter) {
            VisibilityFilter.ALL -> true
            VisibilityFilter.PUBLIC -> !repo.private
            VisibilityFilter.PRIVATE -> repo.private
        }
    }
}

/** Only repositories with explicitly reported admin privileges can be mutated. */
fun canChangeVisibility(repo: GitHubRepository): Boolean =
    repo.permissions?.admin == true &&
        !repo.archived && !repo.fork && repo.visibility != "internal"

class VisibilityConflict(message: String) : IllegalStateException(message)

/**
 * Never retries writes. Re-reads immediately before the mutation and verifies afterward.
 * The caller must request explicit human confirmation before invoking this method.
 */
class RepositoryManager(private val service: GitHubService) {
    suspend fun setPrivate(
        token: String,
        originallyViewed: GitHubRepository,
        targetPrivate: Boolean
    ): GitHubRepository {
        require(canChangeVisibility(originallyViewed)) { "Недостаточно прав на изменение видимости" }
        val latest = service.getRepository(
            token, originallyViewed.owner.login, originallyViewed.name, forceRefresh = true
        )
        if (latest.id != originallyViewed.id || latest.private != originallyViewed.private) {
            throw VisibilityConflict("Репозиторий изменился на сервере. Обновите экран и повторите действие.")
        }
        if (!canChangeVisibility(latest)) {
            throw VisibilityConflict("Административные права или состояние репозитория изменились.")
        }
        if (latest.private == targetPrivate) return latest

        try {
            service.updateVisibility(token, latest.owner.login, latest.name, targetPrivate)
        } catch (error: CancellationException) { throw error }
        catch (error: GitHubApiException) {
            // HTTP 4xx is an explicit rejection; 5xx and transport failures are uncertain.
            if (error.statusCode in 400..499) throw error
            throw VisibilityConflict("Сервер вернул ошибку после PATCH. Проверьте фактическую видимость перед повтором.")
        }
        catch (error: Exception) {
            throw VisibilityConflict(
                "Не удалось получить подтверждение PATCH. Запрос мог быть выполнен. " +
                    "Проверьте фактическую видимость на GitHub перед повтором."
            )
        }
        // If this GET fails, the mutation may nevertheless have succeeded; surface uncertainty.
        val verified = try {
            service.getRepository(token, latest.owner.login, latest.name, forceRefresh = true)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            throw VisibilityConflict(
                "GitHub получил запрос, но результат не удалось проверить. Обновите репозиторий перед повтором."
            )
        }
        if (verified.id != latest.id || verified.private != targetPrivate) {
            throw VisibilityConflict("GitHub не подтвердил новую видимость. Проверьте состояние вручную.")
        }
        return verified
    }
}
