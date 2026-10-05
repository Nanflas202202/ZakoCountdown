// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/UpdateManager.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"

    // 你的云端节点分发地址
    private val MASTER_URLS_FOR_SYNC = listOf(
        "https://raw.githubusercontent.com/Nanflas202202/Nanflas202202.github.io/refs/heads/main/update/zakourls.json",
        "https://nanflas202202.github.io/update/zakourls.json",
        "https://nanflas202202-github-io.pages.dev/update/zakourls.json"
    )

    // --- 【企业级网络请求器】---
    // 突破 Cloudflare/GitHub 防火墙，并设置超时，自动修复拼写错误
    private fun fetchTextFromUrl(urlString: String): String {
        // 自动纠正手滑打出的 "hhttps"
        var fixedUrl = urlString.trim().replace("hhttps://", "https://")

        val connection = URL(fixedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000 // 5秒连接超时
        connection.readTimeout = 5000    // 5秒读取超时

        // 伪装浏览器 UA，防止被 GitHub Pages 和 Cloudflare 当成爬虫拦截
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP Error: ${connection.responseCode} ${connection.responseMessage}")
        }

        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun checkUpdate(context: Context, showToastIfLatest: Boolean = false) {
        withContext(Dispatchers.IO) {
            val pref = PreferenceManager(context)

            // 1. 如果开启了在线同步源列表，先尝试更新本地的 url 列表
            if (pref.isSyncUpdateUrlsEnabled()) {
                syncUrlsFromServer(pref)
            }

            // 2. 获取当前可用的源列表
            val urls = pref.getUpdateUrls()
            var jsonResponse: String? = null

            // 3. 轮询备用源，直到有一个成功
            for (url in urls) {
                try {
                    Log.d(TAG, "Trying to fetch update from: $url")
                    // 使用我们封装的高级请求函数
                    jsonResponse = fetchTextFromUrl(url)
                    break // 成功获取，跳出循环
                } catch (e: Exception) {
                    // 打印详细的异常信息，方便在日志阅读器里排错
                    Log.w(TAG, "Failed to fetch from $url: [${e.javaClass.simpleName}] ${e.message}")
                }
            }

            // 如果全部失败
            if (jsonResponse.isNullOrBlank()) {
                if (showToastIfLatest) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.update_all_sources_unreachable, Toast.LENGTH_SHORT).show()
                    }
                }
                return@withContext
            }

            // 4. 解析并判断版本
            try {
                val json = JSONObject(jsonResponse)
                val latestVersionName = json.getString("versionName")
                val changelog = json.getString("changelog")
                val downloadUrl = json.getString("downloadUrl")

                // 判定「服务端是否有更新」。
                //
                // 关键：**绝不跨刻度比较**。
                // 云端的 versionCode 是随构建次数自增的独立序号（可能是 90 这种小数字），
                // 与我们本地的 versionCode / upgradeCode 根本不是一个量纲。
                // 早先的代码在云端缺少 upgradeCode 时回退去比 versionCode，
                // 于是「云端 90 > 本地 2」就误判成有新版本、每次都弹窗。
                //
                // 现在的优先级：
                //   1. upgradeCode（双方都按同一套规则维护，唯一可信的判据）
                //   2. 云端完全没写 upgradeCode 时，退化为比较 versionName 里的语义化版本号
                //   3. 都无法比较 -> 认为没有更新（宁可不提示，也不误报）
                val hasUpgradeCode = json.has("upgradeCode") && !json.isNull("upgradeCode")
                val latestUpgradeCode = if (hasUpgradeCode) json.getInt("upgradeCode") else null
                val latestVersionCode = if (json.has("versionCode")) json.getInt("versionCode") else null

                val isNewer: Boolean
                val verdict: String
                when {
                    latestUpgradeCode != null -> {
                        isNewer = latestUpgradeCode > BuildConfig.UPGRADE_CODE
                        verdict = "compare upgradeCode: remote=$latestUpgradeCode local=${BuildConfig.UPGRADE_CODE}"
                    }
                    else -> {
                        val cmp = compareSemanticVersions(latestVersionName, BuildConfig.VERSION_NAME)
                        // cmp 为 null 表示版本号格式无法解析 —— 按「没有更新」处理，宁可漏报也不误报
                        isNewer = (cmp ?: 0) > 0
                        verdict = if (cmp == null) {
                            "remote has no upgradeCode and versionName '$latestVersionName' is not comparable -> treat as NOT newer"
                        } else {
                            "compare versionName: remote=$latestVersionName local=${BuildConfig.VERSION_NAME} -> $cmp"
                        }
                    }
                }

                Log.d(
                    TAG,
                    "Update check: remote upgradeCode=$latestUpgradeCode versionCode=$latestVersionCode " +
                            "name=$latestVersionName | local upgradeCode=${BuildConfig.UPGRADE_CODE} " +
                            "name=${BuildConfig.VERSION_NAME} | $verdict | newer=$isNewer"
                )

                withContext(Dispatchers.Main) {
                    if (isNewer) {
                        showUpdateDialog(context, latestVersionName, changelog, downloadUrl)
                    } else if (showToastIfLatest) {
                        Toast.makeText(context, context.getString(R.string.update_already_latest, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON Parsing failed", e)
                withContext(Dispatchers.Main) {
                    if (showToastIfLatest) Toast.makeText(context, R.string.update_parse_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 比较两个语义化版本号（形如 "0.9.1-dev" / "V0.9.2" / "1.0"）。
     *
     * 只取前导的 `数字.数字.数字` 段做逐段整数比较，忽略前缀 `V` 与后缀 `-dev/-beta` 等标记。
     * 仅用于「云端还没提供 upgradeCode」时的降级判据 —— 真正可信的判据始终是 upgradeCode。
     *
     * @return 正数表示 remote 更新；0 表示相同；负数表示 remote 更旧；null 表示无法解析
     */
    private fun compareSemanticVersions(remote: String?, local: String?): Int? {
        val r = parseSemanticVersion(remote) ?: return null
        val l = parseSemanticVersion(local) ?: return null
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv - lv
        }
        return 0
    }

    /** 从任意版本字符串里抠出 `数字.数字.数字`，解析不到返回 null。 */
    private fun parseSemanticVersion(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(raw) ?: return null
        return match.groupValues.drop(1).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    }

    private fun syncUrlsFromServer(pref: PreferenceManager) {
        for (masterUrl in MASTER_URLS_FOR_SYNC) {
            try {
                Log.d(TAG, "Syncing urls from Master: $masterUrl")
                // 使用我们封装的高级请求函数
                val response = fetchTextFromUrl(masterUrl)

                // 兼容性防御：如果你的 JSON 是一个大括号对象 {"urls": ["..."]}，或者直接是个中括号数组 [...]
                val jsonArray = if (response.trim().startsWith("{")) {
                    JSONObject(response).getJSONArray("urls")
                } else {
                    JSONArray(response)
                }

                val newUrls = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    newUrls.add(jsonArray.getString(i))
                }

                if (newUrls.isNotEmpty()) {
                    // 同步成功，覆盖本地设置
                    pref.setUpdateUrls(newUrls.joinToString("\n"))
                    Log.i(TAG, "Urls synced successfully from Master: $masterUrl")
                    break // 成功了就跳出循环，不再尝试下一个 Master
                }
            } catch (e: Exception) {
                // 将真实的报错信息暴露出来
                Log.w(TAG, "Sync failed for Master: $masterUrl - Error: ${e.message}")
            }
        }
    }

    private fun showUpdateDialog(context: Context, versionName: String, changelog: String, url: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.update_found_title, versionName))
            .setMessage(changelog)
            .setPositiveButton(context.getString(R.string.update_download_now)) { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(context, R.string.update_no_browser, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(context.getString(R.string.update_later), null)
            .show()
    }
}