package com.kaze.app.tile

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.kaze.app.KazeApplication

/**
 * Brief foreground focus trampoline used by the Quick Settings Tile so Android permits reading the clipboard,
 * while strictly blocking sensitive password-manager clips (`EXTRA_IS_SENSITIVE`).
 */
class QuickPushActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            false
        }
        if (isSensitive) {
            Toast.makeText(this, "Blocked: Sensitive password clip", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "No text on clipboard", Toast.LENGTH_SHORT).show()
        } else {
            val manager = (application as KazeApplication).connectionManager
            manager.pushClipboardText(text)
            Toast.makeText(this, "Pushed breeze to Mac", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
