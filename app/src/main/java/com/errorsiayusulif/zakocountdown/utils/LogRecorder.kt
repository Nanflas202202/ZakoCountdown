// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/LogRecorder.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRecorder {
    private const val TAG = "LogRecorder"
    private var recordingJob: Job? = null

    fun startRecording(context: Context) {
        if (recordingJob?.isActive == true) {
            Log.d(TAG, "Recording already in progress.")
            return
        }

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Starting log recording session...")

            // 1. 创建日志目录
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()

            // 2. 生成文件名: log_yyyy-MM-dd_HH-mm-ss.txt
            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "log_$timeStamp.txt")

            try {
                // 3. 执行 logcat 命令
                // -v time: 显示时间
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val writer = FileWriter(logFile, true)

                // 4. 持续写入文件
                // --- 【核心修复】初始化为 null ---
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    writer.append(line).append("\n")
                }

                writer.close()
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error recording logs", e)
            }
        }
    }

    fun stopRecording() {
        if (recordingJob?.isActive == true) {
            Log.i(TAG, "Stopping log recording session.")
            recordingJob?.cancel()
            recordingJob = null
        }
    }

    // 获取所有日志文件，按时间倒序排列
    fun getLogFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, "logs")
        return logDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }
}