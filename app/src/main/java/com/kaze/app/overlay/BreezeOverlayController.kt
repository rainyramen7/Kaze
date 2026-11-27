package com.kaze.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kaze.app.net.IncomingToastEvent

/**
 * Floating Suzuri & Persimmon translucent toast overlay (TYPE_APPLICATION_OVERLAY)
 * that slides down gently when Mac pushes a clipboard snippet, without silently overwriting Android's clipboard.
 */
class BreezeOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var dismissRunnable: Runnable? = null

    fun show(event: IncomingToastEvent, showJapanese: Boolean) {
        mainHandler.post {
            dismissInternal()

            val card = buildSuzuriToastView(event, showJapanese)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(42)
            }

            runCatching {
                windowManager.addView(card, params)
                currentView = card
                card.alpha = 0f
                card.translationY = -dp(24).toFloat()
                card.animate().alpha(1f).translationY(0f).setDuration(260).start()

                val task = Runnable { dismissInternal() }
                dismissRunnable = task
                mainHandler.postDelayed(task, 6_500L)
            }
        }
    }

    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    private fun dismissInternal() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        currentView?.let { view ->
            view.animate().cancel()
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private fun buildSuzuriToastView(event: IncomingToastEvent, showJp: Boolean): View {
        val context = appContext
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }

        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#F51D1C1A"))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor("#3A3732"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(10).toFloat()
        }

        // Top persimmon accent bar
        val accentBar = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E05A3A"))
                cornerRadius = dp(2).toFloat()
            }
        }
        card.addView(accentBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
            bottomMargin = dp(10)
        })

        // Header row
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleText = TextView(context).apply {
            text = if (showJp) "受信 · Breeze from ${event.macName}" else "Breeze from ${event.macName}"
            setTextColor(Color.parseColor("#F2EFE9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subText = TextView(context).apply {
            text = "P-256 VERIFIED · ${event.latencyMs ?: 2}ms"
            setTextColor(Color.parseColor("#7FA88E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
        }
        titleCol.addView(titleText)
        titleCol.addView(subText)

        val badge = TextView(context).apply {
            text = event.badge
            setTextColor(Color.parseColor("#E05A3A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24E05A3A"))
                cornerRadius = dp(6).toFloat()
            }
        }
        header.addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(badge)
        card.addView(header)

        // Snippet box (cap rendered preview length to avoid layout stalls on large clips)
        val snippet = TextView(context).apply {
            text = event.text.take(500)
            setTextColor(Color.parseColor("#F2EFE9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141413"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#2E2C28"))
            }
        }
        card.addView(snippet, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })

        // Action buttons row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val copyBtn = TextView(context).apply {
            text = if (showJp) "コピー · Copy to Clipboard" else "Copy to Clipboard"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#141413"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E05A3A"))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Kaze Breeze", event.text))
                Toast.makeText(context, "Copied breeze", Toast.LENGTH_SHORT).show()
                dismissInternal()
            }
        }
        btnRow.addView(copyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val isUrl = event.text.startsWith("http://") || event.text.startsWith("https://")
        if (isUrl) {
            val openBtn = TextView(context).apply {
                text = "Open"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9A958B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#141413"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#2E2C28"))
                }
                setOnClickListener {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.text)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    dismissInternal()
                }
            }
            btnRow.addView(openBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            })
        } else {
            val dismissBtn = TextView(context).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9A958B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#141413"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#2E2C28"))
                }
                setOnClickListener { dismissInternal() }
            }
            btnRow.addView(dismissBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            })
        }

        card.addView(btnRow)
        wrapper.addView(card)
        return wrapper
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), appContext.resources.displayMetrics).toInt()
}
