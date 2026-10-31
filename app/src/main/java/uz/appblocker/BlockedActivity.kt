package uz.appblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Bloklangan ilova yoki Play Market sahifasi ustidan chiqadigan ekran. */
class BlockedActivity : Activity() {

    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        val icon = TextView(this).apply {
            text = "⛔"
            textSize = 72f
            gravity = Gravity.CENTER
        }
        message = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        }
        val hint = TextView(this).apply {
            text = "Bu cheklov telefon egasi tomonidan oʻrnatilgan."
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        }
        val button = Button(this).apply {
            text = "Bosh ekranga qaytish"
            setOnClickListener { goHome() }
        }
        box.addView(icon)
        box.addView(message)
        box.addView(hint)
        box.addView(button)
        setContentView(box)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(i: Intent?) {
        val name = i?.getStringExtra(EXTRA_NAME) ?: "Bu ilova"
        message.text = if (i?.getStringExtra(EXTRA_REASON) == REASON_STORE) {
            "«$name» ilovasini yuklab olish taqiqlangan"
        } else {
            "«$name» ilovasi bloklangan"
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // "Orqaga" bloklangan ilovaga qaytarmasin — bosh ekranga o'tamiz
        startHome()
        super.onBackPressed()
    }

    private fun goHome() {
        startHome()
        finish()
    }

    private fun startHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_NAME = "name"
        const val REASON_APP = "app"
        const val REASON_STORE = "store"
    }
}
