// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/UpdateManager.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.errorsiayusulif.zakocountdown.BuildConfig
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
                        Toast.makeText(context, "所有更新源均无法访问，请检查网络", Toast.LENGTH_SHORT).show()
                    }
                }
                return@withContext
            }

            // 4. 解析并判断版本
            try {
                val json = JSONObject(jsonResponse)
                val latestVersionCode = json.getInt("versionCode")
                val latestVersionName = json.getString("versionName")
                val changelog = json.getString("changelog")
                val downloadUrl = json.getString("downloadUrl")

                withContext(Dispatchers.Main) {
                    if (latestVersionCode > BuildConfig.VERSION_CODE) {
                        showUpdateDialog(context, latestVersionName, changelog, downloadUrl)
                    } else if (showToastIfLatest) {
                        Toast.makeText(context, "当前已是最新版本 (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON Parsing failed", e)
                withContext(Dispatchers.Main) {
                    if (showToastIfLatest) Toast.makeText(context, "更新数据解析失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            .setTitle("发现新版本: $versionName")
            .setMessage(changelog)
            .setPositiveButton("立即下载") { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(context, "找不到浏览器应用", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }
}