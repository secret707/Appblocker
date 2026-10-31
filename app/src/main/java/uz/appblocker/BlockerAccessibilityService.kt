package uz.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 1-usul (Device Owner shart emas):
 *  - bloklangan ilova ochilsa, ustidan "Bloklangan" ekranini chiqaradi;
 *  - Play Market'da bloklangan ilova sahifasi "O'rnatish" tugmasi bilan ochilsa, uni yopadi.
 */
class BlockerAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    @Volatile
    private var rules: List<Rule> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var scanPending = false
    private var lastBlock = 0L
    private val scanRunnable = Runnable {
        scanPending = false
        scanPlayStore()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        rules = Prefs.rules(this)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        GuardService.start(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        rules = Prefs.rules(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val current = rules
        if (current.isEmpty()) return

        // Bloklangan ilovaning oynasi ochildi (faqat oyna almashganda — bosh ekrandagi
        // vidjetlar sababli bloklash takrorlanib qolmasligi uchun)
        val rule = current.firstOrNull { it.pkg == pkg }
        if (rule != null) {
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                block(BlockedActivity.REASON_APP, Blocker.displayName(rule))
            }
            return
        }

        // Play Market — sahifani biroz kutib (ekran to'liq chizilishi uchun) tekshiramiz
        if (pkg == Blocker.PLAY_STORE) scheduleScan()
    }

    private fun scheduleScan() {
        if (scanPending) return
        scanPending = true
        handler.postDelayed(scanRunnable, 350)
    }

    private fun scanPlayStore() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != Blocker.PLAY_STORE) return

        val texts = ArrayList<String>()
        collectTexts(root, texts, IntArray(1), 0)
        val lower = texts.map { it.trim().lowercase() }

        // Sahifada "O'rnatish / Install / Установить" tugmasi bo'lishi shart —
        // shunda tavsiyalar lentasi emas, aynan yuklab olish sahifasi ekani aniq bo'ladi.
        if (lower.none { it in INSTALL_WORDS }) return

        val hit = rules.firstOrNull { r ->
            val name = r.name.trim().lowercase()
            name.isNotEmpty() && lower.any { titleMatches(it, name) }
        } ?: return

        block(BlockedActivity.REASON_STORE, Blocker.displayName(hit))
    }

    /**
     * "instagram" == "instagram"  -> mos
     * "tiktok - videos, music"    -> "tiktok" bilan mos (ajratuvchi belgidan keyin)
     * "telegram x"                -> "telegram" bilan mos EMAS
     */
    private fun titleMatches(text: String, name: String): Boolean {
        if (text == name) return true
        if (!text.startsWith(name)) return false
        val rest = text.substring(name.length).trimStart()
        return rest.isNotEmpty() && rest[0] in SEPARATORS
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, count: IntArray, depth: Int) {
        if (count[0] > 1500 || depth > 60) return
        count[0]++
        node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out, count, depth + 1)
        }
    }

    private fun block(reason: String, name: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlock < 1_500) return
        lastBlock = now
        val i = Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BlockedActivity.EXTRA_REASON, reason)
            .putExtra(BlockedActivity.EXTRA_NAME, name)
        try {
            startActivity(i)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    companion object {
        private const val SEPARATORS = "-–—:|("

        /** Play Market'dagi "O'rnatish" tugmasining turli tillardagi matni. */
        private val INSTALL_WORDS = setOf(
            "install", "установить",
            "oʻrnatish", "o'rnatish", "o‘rnatish", "o`rnatish", "oʼrnatish",
            "yuklab olish",
        )
    }
}
