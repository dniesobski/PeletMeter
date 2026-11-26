package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val apkDownloadUrl: String?
    ) : UpdateCheckState

    data class UpToDate(
        val currentVersion: String,
        val latestTag: String?,
        val repoUrl: String
    ) : UpdateCheckState

    data class NoReleases(
        val latestCommitShort: String?,
        val latestCommitMessage: String?,
        val repoUrl: String
    ) : UpdateCheckState

    data class Error(
        val message: String,
        val repoUrl: String
    ) : UpdateCheckState
}

@JsonClass(generateAdapter = true)
internal data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "assets") val assets: List<GitHubAssetDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class GitHubAssetDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GitHubTagDto(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GitHubCommitItemDto(
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "commit") val commit: GitHubCommitDetailDto? = null
)

@JsonClass(generateAdapter = true)
internal data class GitHubCommitDetailDto(
    @Json(name = "message") val message: String? = null
)

object GitHubUpdateChecker {
    const val REPO_OWNER = "dniesobski"
    const val REPO_NAME = "PeletMeter"
    const val REPO_WEB_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val REPO_RELEASES_URL = "$REPO_WEB_URL/releases"
    private const val API_BASE = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME"

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val releaseAdapter by lazy { moshi.adapter(GitHubReleaseDto::class.java) }
    private val tagsAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubTagDto::class.java)
        moshi.adapter<List<GitHubTagDto>>(type)
    }
    private val commitsAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubCommitItemDto::class.java)
        moshi.adapter<List<GitHubCommitItemDto>>(type)
    }

    suspend fun checkForUpdates(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        try {
            val latestReleaseRes = fetchJson("$API_BASE/releases/latest")
            when (latestReleaseRes.code) {
                200 -> parseLatestRelease(latestReleaseRes.body, currentVersionName)
                404 -> checkTagsOrCommitsFallback(currentVersionName)
                403, 429 -> UpdateCheckState.Error(
                    message = "Przekroczono limit zapytań GitHub API. Spróbuj ponownie za chwilę.",
                    repoUrl = REPO_RELEASES_URL
                )
                else -> UpdateCheckState.Error(
                    message = "Błąd połączenia z GitHub (HTTP ${latestReleaseRes.code}).",
                    repoUrl = REPO_RELEASES_URL
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckState.Error(
                message = "Nie udało się sprawdzić aktualizacji: ${e.localizedMessage ?: "brak połączenia"}",
                repoUrl = REPO_RELEASES_URL
            )
        }
    }

    internal fun parseLatestRelease(jsonBody: String, currentVersionName: String): UpdateCheckState {
        val dto = releaseAdapter.fromJson(jsonBody)
            ?: return UpdateCheckState.Error(
                message = "Nieprawidłowa odpowiedź serwera GitHub.",
                repoUrl = REPO_RELEASES_URL
            )

        val tagName = dto.tagName.orEmpty().trim()
        val releaseName = dto.name?.takeIf { it.isNotBlank() } ?: tagName
        val releaseNotes = dto.body.orEmpty().trim()
        val htmlUrl = dto.htmlUrl?.takeIf { it.isNotBlank() } ?: REPO_RELEASES_URL

        val apkUrl = dto.assets
            ?.firstOrNull { asset ->
                asset.name.orEmpty().endsWith(".apk", ignoreCase = true) &&
                    !asset.browserDownloadUrl.isNullOrBlank()
            }
            ?.browserDownloadUrl

        return if (tagName.isNotBlank() && compareVersions(tagName, currentVersionName) > 0) {
            UpdateCheckState.UpdateAvailable(
                latestVersion = tagName,
                releaseName = releaseName,
                releaseNotes = releaseNotes,
                releaseUrl = htmlUrl,
                apkDownloadUrl = apkUrl
            )
        } else {
            UpdateCheckState.UpToDate(
                currentVersion = currentVersionName,
                latestTag = tagName.ifBlank { null },
                repoUrl = htmlUrl
            )
        }
    }

    private fun checkTagsOrCommitsFallback(currentVersionName: String): UpdateCheckState {
        val repoRes = fetchJson(API_BASE)
        if (repoRes.code == 404) {
            return UpdateCheckState.Error(
                message = "Repozytorium $REPO_OWNER/$REPO_NAME nie jest publicznie dostępne lub nie istnieje.",
                repoUrl = REPO_WEB_URL
            )
        }

        val tagsRes = fetchJson("$API_BASE/tags?per_page=1")
        if (tagsRes.code == 200) {
            val tags = runCatching { tagsAdapter.fromJson(tagsRes.body) }.getOrNull().orEmpty()
            val tagName = tags.firstOrNull()?.name.orEmpty().trim()
            if (tagName.isNotBlank() && compareVersions(tagName, currentVersionName) > 0) {
                return UpdateCheckState.UpdateAvailable(
                    latestVersion = tagName,
                    releaseName = "Tag $tagName",
                    releaseNotes = "",
                    releaseUrl = "$REPO_WEB_URL/releases/tag/$tagName",
                    apkDownloadUrl = null
                )
            } else if (tagName.isNotBlank()) {
                return UpdateCheckState.UpToDate(
                    currentVersion = currentVersionName,
                    latestTag = tagName,
                    repoUrl = REPO_WEB_URL
                )
            }
        }

        val commitsRes = fetchJson("$API_BASE/commits?per_page=1")
        if (commitsRes.code == 200) {
            val commits = runCatching { commitsAdapter.fromJson(commitsRes.body) }.getOrNull().orEmpty()
            val latestCommit = commits.firstOrNull()
            if (latestCommit != null) {
                val sha = latestCommit.sha.orEmpty().take(7).ifBlank { null }
                val message = latestCommit.commit?.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(100)
                return UpdateCheckState.NoReleases(
                    latestCommitShort = sha,
                    latestCommitMessage = message,
                    repoUrl = REPO_WEB_URL
                )
            }
        }

        return UpdateCheckState.NoReleases(
            latestCommitShort = null,
            latestCommitMessage = null,
            repoUrl = REPO_WEB_URL
        )
    }

    /**
     * Compares semantic version strings like "v1.2.3" vs "1.2.0".
     * Returns > 0 if remoteVersion is newer, 0 if equal, < 0 if older.
     */
    fun compareVersions(remoteVersion: String, localVersion: String): Int {
        val remoteParts = parseVersionParts(remoteVersion)
        val localParts = parseVersionParts(localVersion)
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    private fun parseVersionParts(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.')
            .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun fetchJson(urlString: String): HttpResult {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PeletMetr-Android")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
