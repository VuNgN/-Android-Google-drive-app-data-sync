package com.example.googledrive

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView
    private lateinit var backButton: Button
    private lateinit var logScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logTextView = findViewById(R.id.logTextView)
        backButton = findViewById(R.id.backButton)
        logScrollView = findViewById(R.id.logScrollView)

        backButton.setOnClickListener { finish() }

        LogcatHelper.register { log ->
            runOnUiThread {
                log?.let {
                    if (!it.contains("ViewPostIme pointer")) {
                        val spannable = android.text.SpannableString("~ $log\n\n")
                        val color = when {
                            it.contains(" E ") -> getColor(R.color.red) // Error
                            it.contains(" W ") -> getColor(R.color.yellow) // Warning
                            it.contains(" I ") -> getColor(R.color.green) // Info
                            it.contains(" D ") -> getColor(R.color.blue) // Debug
                            else -> getColor(R.color.black) // Info/Other
                        }
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(color),
                            0, spannable.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        logTextView.append(spannable)
                        logScrollView.post {
                            logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogcatHelper.unregister()
    }
}
