package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isUpdateAvailable: Boolean
)

data class UpdateCheckOutcome(
    val info: AppUpdateInfo? = null,
    val errorMessage: String? = null
)

object AppUpdateChecker {
    const val GITHUB_REPO = "Lock001111/MachLog"
    const val RELEASES_API_URL = "https://api.github.com/repos/Lock001111/MachLog/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/Lock001111/MachLog/releases"

    suspend fun checkLatestRelease(
        currentVersionName: String,
        currentVersionCode: Int
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(RELEASES_API_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MachinaLog-Android/$currentVersionName")
            }
            val code = conn.responseCode
            if (code == 404) {
                return@withContext UpdateCheckOutcome(
                    info = AppUpdateInfo(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        latestTag = "v$currentVersionName",
                        versionLabel = "v$currentVersionName",
                        releaseNotes = "No GitHub releases published yet on $GITHUB_REPO.",
                        apkDownloadUrl = null,
                        htmlUrl = RELEASES_PAGE_URL,
                        isUpdateAvailable = false
                    ),
                    errorMessage = "No published GitHub releases found yet for $GITHUB_REPO."
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckOutcome(
                    errorMessage = "GitHub update check failed (HTTP $code)."
                )
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val parsed = parseReleaseJson(
                json = body,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            ) ?: return@withContext UpdateCheckOutcome(errorMessage = "Could not parse GitHub release metadata.")
            UpdateCheckOutcome(info = parsed)
        } catch (t: Throwable) {
            UpdateCheckOutcome(
                errorMessage = "Offline or unable to reach GitHub (${t.javaClass.simpleName})."
            )
        } finally {
            conn?.disconnect()
        }
    }

    fun parseReleaseJson(
        json: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): AppUpdateInfo? {
        return runCatching {
            val obj = JSONObject(json)
            val tagName = obj.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return null
            val releaseName = obj.optString("name", "").trim().ifEmpty { tagName }
            val body = obj.optString("body", "").trim()
            val htmlUrl = obj.optString("html_url", "").trim().ifEmpty { RELEASES_PAGE_URL }

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
            AppUpdateInfo(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestTag = tagName,
                versionLabel = releaseName,
                releaseNotes = body,
                apkDownloadUrl = apkUrl,
                htmlUrl = htmlUrl,
                isUpdateAvailable = newer
            )
        }.getOrNull()
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
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').mapNotNull { seg ->
            seg.trim().takeWhile { it.isDigit() }.toIntOrNull()
        }
    }
}
