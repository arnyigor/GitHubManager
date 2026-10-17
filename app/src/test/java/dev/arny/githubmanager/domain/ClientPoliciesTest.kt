package dev.arny.githubmanager.domain

import org.junit.Assert.*
import org.junit.Test

class ClientPoliciesTest {
    @Test fun cacheTtlAndClockSkew() {
        assertTrue(isFreshCacheEntry(1000, 601000))
        assertFalse(isFreshCacheEntry(1000, 601001))
        assertFalse(isFreshCacheEntry(2000, 1000))
    }

    @Test fun downloadsOnlyRedirectToTrustedHosts() {
        assertTrue(isAllowedAssetRedirectHost("github.com"))
        assertTrue(isAllowedAssetRedirectHost("release-assets.githubusercontent.com"))
        assertFalse(isAllowedAssetRedirectHost("evilgithubusercontent.com"))
        assertFalse(isAllowedAssetRedirectHost("github.com.attacker.net"))
    }

    @Test fun detectMarkdownNames() {
        assertTrue(isMarkdownFile("README.MD"))
        assertTrue(isMarkdownFile("changelog.markdown"))
        assertFalse(isMarkdownFile("README.txt"))
    }

    @Test fun verifiedSha256OnAssets() {
        assertTrue(digestMatches("sha256:Ab12", "ab12"))
        assertFalse(digestMatches("sha256:ffff", "aaaa"))
        assertTrue(digestMatches(null, "abcd"))
    }
}
