package dev.arny.githubmanager.remote

import dev.arny.githubmanager.domain.GitHubApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Remote GitHub Git Database API, NOT a local git engine. No implicit retries or redirects. */
class RemoteGitHubApi {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun endpoint(parts: List<String>, query: Map<String, String> = emptyMap()): HttpUrl =
        HttpUrl.Builder().scheme("https").host("api.github.com").apply {
            parts.forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private suspend fun request(token: String, path: List<String>, method: String = "GET",
                                body: JsonObject? = null, query: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(endpoint(path, query))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("Cache-Control", "no-store")
                .header("User-Agent", "Arny-GitHubManager-Android/0.4")
            if (method != "GET") builder.method(method,
                (body ?: buildJsonObject { }).toString().toRequestBody("application/json".toMediaType()))
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw GitHubApiException(response.code,
                    when(response.code) {
                        401 -> "GitHub отклонил авторизацию"
                        403 -> "Недостаточно прав либо ограничение GitHub API"
                        404 -> "Файл/ветка не найдены"
                        409, 422 -> "Конфликт Git: удалённая ветка или файл изменились, обновите данные"
                        else -> "GitHub API: HTTP ${response.code}"
                    })
                val raw = response.body?.string() ?: throw IOException("Пустой ответ GitHub")
                json.parseToJsonElement(raw)
            }
        }

    private fun base(owner: String, repo: String) = listOf("repos", owner, repo)
    private fun branchParts(owner: String, repo: String, branch: String, plural: Boolean) =
        base(owner, repo) + listOf("git", if (plural) "refs" else "ref", "heads") + branch.split('/')

    suspend fun branches(token: String, owner: String, repo: String): List<String> {
        val arr = request(token, base(owner, repo) + "branches", query = mapOf("per_page" to "100")).jsonArray
        return arr.map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    suspend fun head(token: String, owner: String, repo: String, branch: String): String =
        request(token, branchParts(owner, repo, branch, false)).jsonObject
            .getValue("object").jsonObject.getValue("sha").jsonPrimitive.content

    /** Always reads from network, with ref pinning. Only <=512KiB UTF-8 files may be edited. */
    suspend fun readFile(token: String, owner: String, repo: String, branch: String, path: String): RemoteFile {
        val r = request(token, base(owner, repo) + "contents" + validateGitPath(path).split('/'),
            query = mapOf("ref" to branch)).jsonObject
        require(r.getValue("type").jsonPrimitive.content == "file") { "Редактируются только обычные файлы" }
        require(r.getValue("size").jsonPrimitive.long <= 512 * 1024) { "Файл больше 512 КБ" }
        require(r["encoding"]?.jsonPrimitive?.content == "base64") { "GitHub не вернул содержимое файла" }
        val decoded = Base64.getMimeDecoder().decode(r.getValue("content").jsonPrimitive.content)
        require(decoded.size <= 512 * 1024 && !decoded.contains(0.toByte())) { "Двоичный файл или слишком большой размер" }
        val text = decoded.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(decoded)) { "Файл не является UTF-8" }
        return RemoteFile(path, r.getValue("sha").jsonPrimitive.content, text)
    }

    private suspend fun fileShaOrNull(token: String, owner: String, repo: String, branch: String, path: String): String? {
        return try {
            request(token, base(owner, repo) + "contents" + path.split('/'),
                query = mapOf("ref" to branch)).jsonObject["sha"]?.jsonPrimitive?.content
        } catch (e: GitHubApiException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    /** One atomic server-side commit for all draft changes; never force-updates a ref. */
    suspend fun commit(token: String, owner: String, repo: String, branch: String,
                       changes: List<RemoteChange>, message: String): String {
        validateChanges(changes)
        require(message.isNotBlank() && message.length <= 5000) { "Введите сообщение коммита" }
        val previousHead = head(token, owner, repo, branch)
        // Before building a tree, verify each original blob SHA. This also checks new paths don't exist.
        for (change in changes) {
            val actual = fileShaOrNull(token, owner, repo, branch, change.path)
            if (actual != change.originalSha) throw IOException("Конфликт файла ${change.path}. Черновики сохранены.")
        }
        val commitInfo = request(token, base(owner, repo) + listOf("git", "commits", previousHead)).jsonObject
        val baseTree = commitInfo.getValue("tree").jsonObject.getValue("sha").jsonPrimitive.content
        val newEntries = mutableListOf<JsonElement>()
        for (change in changes) {
                val newSha = if (change.type == ChangeType.DELETE) null else {
                    val blob = request(token, base(owner, repo) + listOf("git", "blobs"), "POST", buildJsonObject {
                        put("content", Base64.getEncoder().encodeToString(change.content!!.toByteArray(Charsets.UTF_8)))
                        put("encoding", "base64")
                    }).jsonObject
                    blob.getValue("sha").jsonPrimitive.content
                }
                newEntries.add(buildJsonObject {
                    put("path", change.path)
                    put("mode", "100644")
                    put("type", "blob")
                    if (newSha == null) put("sha", JsonNull) else put("sha", newSha)
                })
        }
        val entries = buildJsonArray { newEntries.forEach { add(it) } }
        val tree = request(token, base(owner, repo) + listOf("git", "trees"), "POST", buildJsonObject {
            put("base_tree", baseTree)
            put("tree", entries)
        }).jsonObject.getValue("sha").jsonPrimitive.content
        val newCommit = request(token, base(owner, repo) + listOf("git", "commits"), "POST", buildJsonObject {
            put("message", message.trim())
            put("tree", tree)
            put("parents", buildJsonArray { add(previousHead) })
        }).jsonObject.getValue("sha").jsonPrimitive.content
        // force=false: if HEAD advanced between preflight and PATCH, GitHub rejects non-fast-forward.
        request(token, branchParts(owner, repo, branch, true), "PATCH", buildJsonObject {
            put("sha", newCommit)
            put("force", false)
        })
        return newCommit
    }
}
