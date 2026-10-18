package dev.arny.githubmanager.domain

import org.junit.Assert.*
import org.junit.Test

class ReliabilityPoliciesTest {

    // Pagination: the `Link` header decides, not the page size.
    @Test fun nextLinkIsParsed() {
        val header = "<https://api.github.com/user/repos?page=2>; rel=\"next\", " +
            "<https://api.github.com/user/repos?page=5>; rel=\"last\""
        assertEquals("https://api.github.com/user/repos?page=2", nextPageUrl(header))
    }

    @Test fun lastPageHasNoNextLink() {
        val header = "<https://api.github.com/user/repos?page=4>; rel=\"prev\", " +
            "<https://api.github.com/user/repos?page=1>; rel=\"first\""
        assertNull(nextPageUrl(header))
        assertFalse(hasNextPage(header, 50, 50))
    }

    @Test fun unquotedAndSpacedRelIsAccepted() {
        val header = "<https://api.github.com/x?page=3>; rel=next; type=\"application/json\""
        assertEquals("https://api.github.com/x?page=3", nextPageUrl(header))
    }

    @Test fun missingLinkHeaderFallsBackToPageSize() {
        assertTrue(hasNextPage(null, 50, 50))
        assertFalse(hasNextPage(null, 17, 50))
        assertFalse(hasNextPage(null, 0, 50))
    }

    @Test fun exactlyFullLastPageWithLinkIsNotMore() {
        // 50 items and a Link without rel=next: the old `size == perPage` rule produced a phantom page.
        assertFalse(hasNextPage("<https://api.github.com/x?page=1>; rel=\"first\"", 50, 50))
    }

    @Test fun realLastPageHeaderFromGitHubHasNoNext() {
        // Verbatim header of /user/repos per_page=50 page=2 for an account with 64 repositories.
        val lastPage = "<https://api.github.com/user/repos?affiliation=owner%2Ccollaborator%2Corganization_member" +
            "&sort=updated&direction=desc&per_page=50&page=1>; rel=\"prev\", " +
            "<https://api.github.com/user/repos?affiliation=owner%2Ccollaborator%2Corganization_member" +
            "&sort=updated&direction=desc&per_page=50&page=1>; rel=\"first\""
        assertFalse(hasNextPage(lastPage, 14, 50))
        assertNull(nextPageUrl(lastPage))
    }

    // 403 must be told apart: permissions vs primary vs secondary rate limit.
    @Test fun forbiddenKindsAreDistinguished() {
        assertEquals(ApiFailureKind.RATE_LIMIT, classifyFailure(403, rateLimitRemaining = 0))
        assertEquals(ApiFailureKind.SECONDARY_RATE_LIMIT, classifyFailure(403, rateLimitRemaining = 42, retryAfterSeconds = 30))
        assertEquals(ApiFailureKind.FORBIDDEN, classifyFailure(403, rateLimitRemaining = 4999))
        assertEquals(ApiFailureKind.RATE_LIMIT, classifyFailure(429))
        assertEquals(ApiFailureKind.UNAUTHORIZED, classifyFailure(401))
    }

    @Test fun rateLimitMessageNamesRecoveryTime() {
        val now = 1_700_000_000L
        val message = failureMessage(403, ApiFailureKind.RATE_LIMIT, resetAt = now + 600, now = now)
        assertTrue(message, message.contains("лимит запросов"))
        assertTrue(message, message.contains("Лимит восстановится в "))
        val expired = failureMessage(403, ApiFailureKind.RATE_LIMIT, resetAt = now - 10, now = now)
        assertTrue(expired, expired.contains("Повторите позже"))
    }

    @Test fun secondaryLimitUsesRetryAfter() {
        val message = failureMessage(403, ApiFailureKind.SECONDARY_RATE_LIMIT, retryAfterSeconds = 120)
        assertTrue(message, message.contains("вторичный лимит"))
        assertTrue(message, message.contains("2 мин"))
    }

    @Test fun plainForbiddenDoesNotMentionLimits() {
        val message = failureMessage(403, ApiFailureKind.FORBIDDEN)
        assertTrue(message, message.contains("Недостаточно прав"))
        assertFalse(message, message.contains("лимит"))
    }
}
