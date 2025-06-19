package com.example.googledrive

import android.os.Handler
import android.os.Looper

object LogcatHelper {
    private var running = false
    private var logThread: Thread? = null

    fun register(onLogUpdate: (String?) -> Unit) {
        running = true
        logThread = Thread {
            try {
                val process = Runtime.getRuntime().exec("logcat")
                process.inputStream.bufferedReader().use { reader ->
                    val handler = Handler(Looper.getMainLooper())
                    var line: String?
                    while (running) {
                        line = reader.readLine()
                        if (line != null) {
                            handler.post {
                                onLogUpdate(line)
                            }
                        } else {
                            Thread.sleep(200)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        logThread?.start()
    }

    fun unregister() {
        running = false
        logThread = null
    }
}