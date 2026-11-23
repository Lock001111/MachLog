package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestTag: String,
    val versionLabel: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean,
    val isPreRelease: Boolean = false
)

data class UpdateCheckOutcome(
    val info: AppUpdateInfo? = null,
    val errorMessage: String? = null
)

object AppUpdateChecker {
    const val GITHUB_REPO = "Lock001111/MachLog"
    const val RELEASES_LIST_API_URL = "https://api.github.com/repos/Lock001111/MachLog/releases?per_page=10"
    const val RELEASES_API_URL = "https://api.github.com/repos/Lock001111/MachLog/releases/latest"
    const val TAGS_API_URL = "https://api.github.com/repos/Lock001111/MachLog/tags?per_page=5"
    const val RELEASES_PAGE_URL = "https://github.com/Lock001111/MachLog/releases"

    fun normalizeRepoSlug(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix(".git")
            .trim('/')
        return if (cleaned.contains('/')) cleaned else GITHUB_REPO
    }

    suspend fun checkLatestRelease(
        currentVersionName: String,
        currentVersionCode: Int,
        githubRepo: String = GITHUB_REPO,
        githubToken: String = ""
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val repoSlug = normalizeRepoSlug(githubRepo)
        val token = githubToken.trim()
        val listUrl = "https://api.github.com/repos/$repoSlug/releases?per_page=10"
        val latestUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        val tagsUrl = "https://api.github.com/repos/$repoSlug/tags?per_page=5"
        val releasesPageUrl = "https://github.com/$repoSlug/releases"

        try {
            // Query /releases?per_page=10 so pre-releases and newly tagged builds are included
            // (GitHub's /releases/latest returns 404 if a release is marked pre-release).
            val listResp = httpGet(listUrl, currentVersionName, token)
            if (listResp.code in 200..299) {
                val parsed = parseReleasesArrayJson(
                    json = listResp.body,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
                if (parsed != null) {
                    return@withContext UpdateCheckOutcome(info = parsed)
                }
            }

            // Fallback to /releases/latest (only if list didn't fail auth/404)
            val latestResp = if (listResp.code == 401 || listResp.code == 404) {
                listResp
            } else {
                httpGet(latestUrl, currentVersionName, token)
            }
            if (latestResp.code in 200..299) {
                val parsed = parseReleaseJson(
                    json = latestResp.body,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
                if (parsed != null) {
                    return@withContext UpdateCheckOutcome(info = parsed)
                }
            }

            if (listResp.code == 401 || latestResp.code == 401) {
                return@withContext UpdateCheckOutcome(
                    errorMessage = "GitHub Token rejected (HTTP 401 Unauthorized) for $repoSlug. Check or clear your token in App Updates settings."
                )
            }

            if (listResp.code == 403 || latestResp.code == 403) {
                return@withContext UpdateCheckOutcome(
                    errorMessage = "GitHub API rate limit reached or repo access denied (HTTP 403). Add a read-only GitHub Token or try again later."
                )
            }

            if (listResp.code == 404) {
                return@withContext UpdateCheckOutcome(
                    errorMessage = "GitHub returned HTTP 404 for \"$repoSlug\". If the repository is Private, either make it Public on GitHub or add a read-only GitHub Token below."
                )
            }

            // Repo is reachable (HTTP 200) but has 0 non-draft releases — check /tags to give a clear hint
            val tagsResp = httpGet(tagsUrl, currentVersionName, token)
            val tagHint = if (tagsResp.code in 200..299) {
                runCatching {
                    val arr = JSONArray(tagsResp.body)
                    if (arr.length() > 0) {
                        val topTag = arr.optJSONObject(0)?.optString("name", "").orEmpty()
                        if (topTag.isNotEmpty()) {
                            "Git tag \"$topTag\" exists on $repoSlug, but no published GitHub Release + APK was found. Check the GitHub Actions workflow or publish a Release."
                        } else null
                    } else null
                }.getOrNull()
            } else null

            return@withContext UpdateCheckOutcome(
                info = AppUpdateInfo(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    latestTag = "v$currentVersionName",
                    versionLabel = "v$currentVersionName",
                    releaseNotes = tagHint ?: "Connected to $repoSlug, but 0 published GitHub Releases exist yet.",
                    apkDownloadUrl = null,
                    htmlUrl = releasesPageUrl,
                    isUpdateAvailable = false
                ),
                errorMessage = tagHint ?: "Connected to $repoSlug, but 0 published GitHub Releases were found (drafts are ignored)."
            )
        } catch (t: Throwable) {
            UpdateCheckOutcome(
                errorMessage = "Offline or unable to reach GitHub (${t.javaClass.simpleName})."
            )
        }
    }

    private data class HttpFetchResult(val code: Int, val body: String)

    private fun httpGet(urlStr: String, currentVersionName: String, githubToken: String = ""): HttpFetchResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MachinaLog-Android/$currentVersionName")
                if (githubToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $githubToken")
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            HttpFetchResult(code, body)
        } finally {
            conn?.disconnect()
        }
    }

    fun parseReleasesArrayJson(
        json: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): AppUpdateInfo? {
        return runCatching {
            val arr = JSONArray(json)
            val candidates = mutableListOf<AppUpdateInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("draft", false)) continue
                val info = parseReleaseObject(obj, currentVersionName, currentVersionCode) ?: continue
                candidates += info
            }
            if (candidates.isEmpty()) return null

            // Prefer the candidate with the highest semantic/numeric version (and APK asset if tied)
            candidates.maxWithOrNull { a, b ->
                val verCmp = compareVersionStrings(a.latestTag, b.latestTag)
                if (verCmp != 0) verCmp
                else {
                    val aHasApk = if (a.apkDownloadUrl != null) 1 else 0
                    val bHasApk = if (b.apkDownloadUrl != null) 1 else 0
                    aHasApk.compareTo(bHasApk)
                }
            } ?: candidates.first()
        }.getOrNull()
    }

    fun parseReleaseJson(
        json: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): AppUpdateInfo? {
        return runCatching {
            val obj = JSONObject(json)
            parseReleaseObject(obj, currentVersionName, currentVersionCode)
        }.getOrNull()
    }

    private fun parseReleaseObject(
        obj: JSONObject,
        currentVersionName: String,
        currentVersionCode: Int
    ): AppUpdateInfo? {
        val tagName = obj.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null
        val releaseName = obj.optString("name", "").trim().ifEmpty { tagName }
        val body = obj.optString("body", "").trim()
        val htmlUrl = obj.optString("html_url", "").trim().ifEmpty { RELEASES_PAGE_URL }
        val isPreRelease = obj.optBoolean("prerelease", false)

        var apkUrl: String? = null
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkUrl = downloadUrl
                    break
                }
            }
        }

        val newer = isRemoteVersionNewer(
            remoteTag = tagName,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode
        )
        return AppUpdateInfo(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestTag = tagName,
            versionLabel = releaseName,
            releaseNotes = body,
            apkDownloadUrl = apkUrl,
            htmlUrl = htmlUrl,
            isUpdateAvailable = newer,
            isPreRelease = isPreRelease
        )
    }

    fun compareVersionStrings(aRaw: String, bRaw: String): Int {
        val aParts = parseVersionParts(aRaw)
        val bParts = parseVersionParts(bRaw)
        if (aParts.isEmpty() || bParts.isEmpty()) return 0
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isRemoteVersionNewer(
        remoteTag: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): Boolean {
        val remoteParts = parseVersionParts(remoteTag)
        val currentParts = parseVersionParts(currentVersionName)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            val normalizedRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
            val normalizedCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")
            return normalizedRemote.isNotEmpty() &&
                !normalizedRemote.equals(normalizedCurrent, ignoreCase = true) &&
                currentVersionCode >= 0
        }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    internal fun parseVersionParts(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('+')
        if (cleaned.isEmpty()) return emptyList()
        // Extract first dotted numeric sequence (e.g. "1.0.5" from "v1.0.5", "release-1.2.3-beta", "build-4")
        val match = Regex("""\d+(?:\.\d+)*""").find(cleaned) ?: return emptyList()
        return match.value.split('.').mapNotNull { it.toIntOrNull() }
    }
}
