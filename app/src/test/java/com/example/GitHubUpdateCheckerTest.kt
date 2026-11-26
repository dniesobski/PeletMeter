package com.example

import com.example.data.GitHubUpdateChecker
import com.example.data.UpdateCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun compareVersions_handlesPrefixesAndPatchNumbers() {
        assertTrue(GitHubUpdateChecker.compareVersions("v1.2.0", "1.0") > 0)
        assertTrue(GitHubUpdateChecker.compareVersions("v1.0.1", "1.0") > 0)
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.0.0", "1.0"))
        assertTrue(GitHubUpdateChecker.compareVersions("v0.9.5", "1.0") < 0)
    }

    @Test
    fun parseLatestRelease_detectsNewerReleaseAndApkAsset() {
        val json = """
            {
              "tag_name": "v1.3.0",
              "name": "PeletMetr 1.3.0",
              "body": "Dodano wykres kosztów",
              "html_url": "https://github.com/dniesobski/PeletMeter/releases/tag/v1.3.0",
              "assets": [
                {
                  "name": "peletmetr-release.apk",
                  "browser_download_url": "https://github.com/dniesobski/PeletMeter/releases/download/v1.3.0/peletmetr-release.apk"
                }
              ]
            }
        """.trimIndent()

        val state = GitHubUpdateChecker.parseLatestRelease(json, "1.0")
        assertTrue(state is UpdateCheckState.UpdateAvailable)
        val available = state as UpdateCheckState.UpdateAvailable
        assertEquals("v1.3.0", available.latestVersion)
        assertEquals(
            "https://github.com/dniesobski/PeletMeter/releases/download/v1.3.0/peletmetr-release.apk",
            available.apkDownloadUrl
        )
    }

    @Test
    fun parseLatestRelease_reportsUpToDateWhenVersionMatches() {
        val json = """
            {
              "tag_name": "v1.0",
              "name": "PeletMetr 1.0",
              "html_url": "https://github.com/dniesobski/PeletMeter/releases/tag/v1.0",
              "assets": []
            }
        """.trimIndent()

        val state = GitHubUpdateChecker.parseLatestRelease(json, "1.0")
        assertTrue(state is UpdateCheckState.UpToDate)
    }
}
