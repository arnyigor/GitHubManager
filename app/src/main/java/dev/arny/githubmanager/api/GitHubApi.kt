package dev.arny.githubmanager.api

import android.content.Context
import dev.arny.githubmanager.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GitHubApi(context: Context) : GitHubService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val cache = EncryptedResponseCache(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    // Streaming downloads must NOT have a callTimeout cap; streamed via IO and cancellable job.
    private val downloadClient = client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()

    private fun url(segments: List<String>, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = HttpUrl.Builder().scheme("https").host("api.github.com")
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun request(token: String, url: HttpUrl, accept: String = "application/vnd.github+json"): Request.Builder =
        Request.Builder().url(url)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Arny-GitHubManager-Android/0.3")

    private fun failure(code: Int) = GitHubApiException(code, failureMessage(code, classifyFailure(code)))

    /** 403 is ambiguous, so the reason is built from the rate-limit headers rather than from the code alone. */
    private fun failure(response: Response): GitHubApiException {
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        val retryAfter = response.header("Retry-After")?.toLongOrNull()
        val resetAt = response.header("X-RateLimit-Reset")?.toLongOrNull()
        val kind = classifyFailure(response.code, remaining, retryAfter)
        return GitHubApiException(response.code, failureMessage(response.code, kind, resetAt, retryAfter))
    }

    private data class ApiResponse(val body: String, val source: CacheSource, val link: String?)

    /** OkHttp `execute()` cannot be cancelled from a coroutine; `enqueue` + cancel on coroutine cancel can. */
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    /** Normal reads: 10-minute encrypted cache, stale conditional GET via ETag, offline fallback.
     * forceRefresh: always network, never If-None-Match or stale fallback, replaces saved data.
     * Writes: never cached. */
    private suspend fun call(
        token: String,
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        patchBody: String? = null,
        forceRefresh: Boolean = false
    ): ApiResponse = withContext(Dispatchers.IO) {
        val target = url(segments, query)
        val cached = if (patchBody == null && !forceRefresh) cache.get(token, target.toString()) else null
        if (cached != null && cache.isFresh(cached)) {
            return@withContext ApiResponse(cached.body, CacheSource.FRESH_CACHE, null)
        }

        val builder = request(token, target).header("Cache-Control", "no-store")
        if (patchBody != null) {
            builder.patch(patchBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else if (cached?.etag != null) {
            builder.header("If-None-Match", cached.etag)
        }
        try {
            client.newCall(builder.build()).awaitResponse().use { response ->
                if (response.code == 304 && cached != null) {
                    cache.put(token, target.toString(), cached.body, cached.etag)
                    return@withContext ApiResponse(cached.body, CacheSource.VALIDATED_304, response.header("Link"))
                }
                if (!response.isSuccessful) throw failure(response)
                val body = response.body?.string() ?: ""
                if (patchBody == null) cache.put(token, target.toString(), body, response.header("ETag"))
                ApiResponse(body, CacheSource.NETWORK, response.header("Link"))
            }
        } catch (e: GitHubApiException) { throw e }
        catch (e: IOException) {
            if (cached != null) ApiResponse(cached.body, CacheSource.STALE_OFFLINE, null)
            else throw IOException("Ошибка сети. Проверьте подключение к GitHub.")
        }
    }

    private suspend fun callBody(
        token: String,
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        patchBody: String? = null,
        forceRefresh: Boolean = false
    ): String = call(token, segments, query, patchBody, forceRefresh).body

    override fun clearCache() = cache.clear()

    override suspend fun currentUser(token: String): GitHubUser =
        json.decodeFromString(callBody(token, listOf("user")))

    override suspend fun listRepositories(token: String, page: Int, forceRefresh: Boolean): RepoPage {
        require(page > 0)
        val response = call(token, listOf("user", "repos"), mapOf(
            "affiliation" to "owner,collaborator,organization_member",
            "sort" to "updated", "direction" to "desc",
            "per_page" to "50", "page" to page.toString()
        ), forceRefresh = forceRefresh)
        val repos: List<GitHubRepository> = json.decodeFromString(response.body)
        return RepoPage(repos, hasNextPage(response.link, repos.size, 50), response.source)
    }

    override suspend fun getRepository(token: String, owner: String, name: String, forceRefresh: Boolean): GitHubRepository =
        json.decodeFromString(callBody(token, listOf("repos", owner, name), forceRefresh = forceRefresh))

    override suspend fun updateVisibility(token: String, owner: String, name: String, makePrivate: Boolean) {
        val body = buildJsonObject { put("visibility", if (makePrivate) "private" else "public") }.toString()
        callBody(token, listOf("repos", owner, name), patchBody = body)
        // Purge old repository and list cache after mutation: do not display stale visibility.
        cache.clear()
    }

    override suspend fun getContent(
        token: String, owner: String, name: String, path: String, forceRefresh: Boolean
    ): DirectoryResult {
        val segments = listOf("repos", owner, name, "contents") + path.split('/').filter { it.isNotBlank() }
        val body = callBody(token, segments, forceRefresh = forceRefresh)
        return if (body.trimStart().startsWith("[")) {
            DirectoryResult.Directory(json.decodeFromString(body))
        } else DirectoryResult.File(json.decodeFromString(body))
    }

    /** GitHub caps the JSON `content` field (1 MB), so bigger files are streamed through the raw media type. */
    override suspend fun getRawContent(
        token: String, owner: String, name: String, path: String, maxBytes: Long
    ): ByteArray = withContext(Dispatchers.IO) {
        val target = url(listOf("repos", owner, name, "contents") + path.split('/').filter { it.isNotBlank() })
        val request = request(token, target, "application/vnd.github.raw").header("Cache-Control", "no-store")
        client.newCall(request.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) throw failure(response)
            val body = response.body ?: throw IOException("GitHub не вернул файл")
            val tooBig = "Файл больше ${maxBytes / 1024 / 1024} МБ. Откройте его на GitHub."
            if (body.contentLength() > maxBytes) throw IOException(tooBig)
            val sink = java.io.ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw IOException(tooBig)
                    sink.write(buffer, 0, read)
                }
            }
            sink.toByteArray()
        }
    }

    override suspend fun getContentAtRef(
        token: String, owner: String, name: String, path: String, ref: String, forceRefresh: Boolean
    ): DirectoryResult {
        val segments = listOf("repos", owner, name, "contents") + path.split('/').filter { it.isNotBlank() }
        val body = callBody(token, segments, query = mapOf("ref" to ref), forceRefresh = forceRefresh)
        return if (body.trimStart().startsWith("[")) {
            DirectoryResult.Directory(json.decodeFromString(body))
        } else DirectoryResult.File(json.decodeFromString(body))
    }

    override suspend fun getRawContentAtRef(
        token: String, owner: String, name: String, path: String, ref: String, maxBytes: Long
    ): ByteArray = withContext(Dispatchers.IO) {
        val target = url(listOf("repos", owner, name, "contents") + path.split('/').filter { it.isNotBlank() },
            query = mapOf("ref" to ref))
        val request = request(token, target, "application/vnd.github.raw").header("Cache-Control", "no-store")
        client.newCall(request.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) throw failure(response)
            val body = response.body ?: throw IOException("GitHub не вернул файл")
            val tooBig = "Файл больше ${maxBytes / 1024 / 1024} МБ. Откройте его на GitHub."
            if (body.contentLength() > maxBytes) throw IOException(tooBig)
            val sink = java.io.ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw IOException(tooBig)
                    sink.write(buffer, 0, read)
                }
            }
            sink.toByteArray()
        }
    }

    override suspend fun listReleases(
        token: String, owner: String, name: String, page: Int, forceRefresh: Boolean
    ): ReleasePage {
        require(page > 0)
        val response = call(token, listOf("repos", owner, name, "releases"),
            query = mapOf("page" to page.toString(), "per_page" to "30"), forceRefresh = forceRefresh)
        val releases: List<GitHubRelease> = json.decodeFromString(response.body)
        return ReleasePage(releases, hasNextPage(response.link, releases.size, 30), response.source)
    }

    /** Only the initial api.github.com request contains the PAT. Signed CDN redirects get no token. */
    override suspend fun downloadAsset(
        token: String, owner: String, name: String, asset: GitHubReleaseAsset,
        target: OutputStream, onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        var current = url(listOf("repos", owner, name, "releases", "assets", asset.id.toString()))
        var initial = true
        for (redirects in 0..5) {
            val builder = if (initial) request(token, current, "application/octet-stream")
            else Request.Builder().url(current).header("User-Agent", "Arny-GitHubManager-Android/0.3")
            downloadClient.newCall(builder.build()).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location") ?: throw IOException("GitHub не прислал адрес загрузки")
                    val next = current.resolve(location) ?: throw IOException("Некорректный адрес перенаправления")
                    require(next.isHttps && (isAllowedAssetRedirectHost(next.host))) {
                        "Недопустимый хост перенаправления"
                    }
                    current = next
                    initial = false
                } else {
                    if (!response.isSuccessful) throw failure(response.code)
                    if (response.header("Content-Type")?.contains("application/json", true) == true)
                        throw IOException("GitHub вернул JSON вместо файла")
                    val body = response.body ?: throw IOException("GitHub не вернул файл")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    body.byteStream().use { source ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = source.read(buffer)
                            if (n == -1) break
                            target.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            received += n
                            onProgress(received, asset.size)
                        }
                    }
                    target.flush()
                    if (asset.size > 0 && asset.size != received) throw IOException("Неполная загрузка файла")
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!digestMatches(asset.digest, actual)) throw IOException("Контрольная сумма SHA-256 не совпадает")
                    return@withContext
                }
            }
        }
        throw IOException("Слишком много перенаправлений при загрузке")
    }
}
