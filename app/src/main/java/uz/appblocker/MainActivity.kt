package uz.appblocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Asosiy ekran: holat, bloklash ro'yxati, Device Owner sozlamalari va PIN. */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private var unlocked = false
    private var internalNav = false
    private var pinDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!Prefs.hasPin(this)) unlocked = true
    }

    override fun onResume() {
        super.onResume()
        internalNav = false
        if (unlocked) {
            GuardService.start(this)
            render()
        } else {
            root.removeAllViews()
            askPin()
        }
    }

    override fun onStop() {
        super.onStop()
        // Ilovadan chiqib ketilsa, qaytishda PIN yana so'raladi
        if (!internalNav && Prefs.hasPin(this)) unlocked = false
    }

    // ================= Ekran =================

    private fun render() {
        root.removeAllViews()
        val acc = Blocker.isAccessibilityOn(this)
        val owner = Blocker.isDeviceOwner(this)

        header("Holat")
        statusRow("Accessibility xizmati", acc, if (acc) "yoqilgan" else "oʻchiq")
        statusRow("Device Owner rejimi", owner, if (owner) "faol" else "oʻrnatilmagan")
        if (!acc) {
            note(
                "Play Market'da bloklangan ilova sahifasini yopish va ilovani ochishni toʻsish uchun " +
                    "Accessibility'da «Ilova Bloklagich»ni yoqing.\n\n" +
                    "Android 13+ da yoqib boʻlmasa: «Ilova maʼlumotlari» → ⋮ (yuqori oʻng burchak) → " +
                    "«Cheklangan sozlamalarga ruxsat berish», keyin qayta urinib koʻring."
            )
            button("Accessibility sozlamalarini ochish") {
                openExternal(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            button("Ilova maʼlumotlarini ochish") {
                openExternal(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        if (!owner) {
            note(
                "Device Owner rejimida bloklangan ilova oʻrnatilishi bilanoq avtomatik oʻchiriladi " +
                    "va Bloklagichning oʻzini oʻchirib boʻlmaydi. Yoqish uchun bir martalik adb buyrugʻi kerak " +
                    "(README faylida)."
            )
        }

        header("Bloklangan ilovalar")
        val rules = Prefs.rules(this)
        if (rules.isEmpty()) note("Roʻyxat boʻsh. Pastdan ilova qoʻshing.")
        rules.forEach { ruleRow(it) }

        header("Yangi ilova qoʻshish")
        val linkInput = input("Play Market havolasi yoki paket nomi", uri = true)
        note("Masalan: play.google.com/store/apps/details?id=com.instagram.android yoki shunchaki com.instagram.android")
        val nameInput = input("Play Market'dagi nomi (masalan: Instagram)", uri = false)
        button("Qoʻshish") { addFromInputs(linkInput.text.toString(), nameInput.text.toString()) }
        button("Oʻrnatilgan ilovalardan tanlash") { pickInstalled() }

        if (owner) {
            header("Device Owner sozlamalari")
            note("Bloklagichni oʻchirib boʻlmaydi. Qoʻshimcha cheklovlar (ixtiyoriy):")
            Blocker.OWNER_RESTRICTIONS.forEach { (key, label) ->
                switchRow(label, Blocker.hasRestriction(this, key)) { on -> Blocker.setRestriction(this, key, on) }
            }
            button("Device Owner rejimidan chiqish") { confirmReleaseOwner() }
        }

        header("PIN himoya")
        val hasPin = Prefs.hasPin(this)
        note(
            if (hasPin) "PIN oʻrnatilgan — roʻyxatni faqat PIN bilan oʻzgartirish mumkin."
            else "PIN oʻrnatilmagan — telefonni ushlagan har kim roʻyxatni oʻzgartira oladi."
        )
        button(if (hasPin) "PIN'ni oʻzgartirish" else "PIN oʻrnatish") { setPinDialog() }
        if (hasPin) {
            button("PIN'ni olib tashlash") {
                Prefs.setPin(this, null)
                toast("PIN olib tashlandi")
                render()
            }
        }
    }

    private fun ruleRow(rule: Rule) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F4F6"))
                cornerRadius = dp(10).toFloat()
            }
        }
        val installed = Blocker.isInstalled(this, rule.pkg)
        val info = TextView(this).apply {
            text = buildString {
                append(Blocker.displayName(rule))
                append("\n").append(rule.pkg)
                if (installed) append("\n⚠ telefonda oʻrnatilgan")
            }
            textSize = 15f
            setTextColor(Color.parseColor("#111827"))
        }
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Olib tashlash"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { confirmRemove(rule) }
        })
        root.addView(row, lp(top = 8))
    }

    // ================= Amallar =================

    private fun addFromInputs(link: String, nameRaw: String) {
        val pkg = Blocker.parsePackage(link)
        if (pkg == null) {
            toast("Havola yoki paket nomi notoʻgʻri")
            return
        }
        if (Blocker.isProtected(this, pkg)) {
            toast("Bu tizim ilovasini bloklab boʻlmaydi")
            return
        }
        val name = nameRaw.trim().ifEmpty { Blocker.labelOf(this, pkg) ?: "" }
        if (name.isEmpty()) {
            toast("Play Market'dagi nomini ham yozing — sahifani aniqlash uchun kerak")
            return
        }
        confirmAdd(Rule(pkg, name))
    }

    private fun confirmAdd(rule: Rule) {
        if (!Blocker.isInstalled(this, rule.pkg)) {
            doAdd(rule)
            return
        }
        val what = if (Blocker.isDeviceOwner(this)) "darhol avtomatik oʻchiriladi" else "uni oʻchirish soʻraladi"
        AlertDialog.Builder(this)
            .setTitle("«${rule.name}» telefonda oʻrnatilgan")
            .setMessage("Bloklangandan soʻng ilova $what. Davom etasizmi?")
            .setPositiveButton("Ha, bloklash") { _, _ -> doAdd(rule) }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun doAdd(rule: Rule) {
        Prefs.addRule(this, rule)
        if (Blocker.isInstalled(this, rule.pkg)) {
            internalNav = true
            Blocker.onPackageAdded(this, rule.pkg, interactive = true)
        }
        GuardService.start(this)
        toast("«${rule.name}» bloklandi")
        render()
    }

    private fun confirmRemove(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle("«${Blocker.displayName(rule)}» blokdan chiqarilsinmi?")
            .setPositiveButton("Ha") { _, _ ->
                Prefs.removeRule(this, rule.pkg)
                Blocker.unsuspend(this, rule.pkg)
                if (Prefs.rules(this).isEmpty()) GuardService.stop(this)
                render()
            }
            .setNegativeButton("Yoʻq", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickInstalled() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filterNot { Blocker.isProtected(this, it.first) }
            .sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) {
            toast("Ilovalar topilmadi")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Qaysi ilovani bloklash kerak?")
            .setItems(apps.map { "${it.second}\n${it.first}" }.toTypedArray()) { _, i ->
                val (pkg, name) = apps[i]
                confirmAdd(Rule(pkg, name))
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun confirmReleaseOwner() {
        AlertDialog.Builder(this)
            .setTitle("Device Owner rejimidan chiqilsinmi?")
            .setMessage(
                "Qoʻshimcha cheklovlar olib tashlanadi, muzlatilgan ilovalar ochiladi va Bloklagichni " +
                    "oddiy usulda oʻchirish mumkin boʻladi. Qaytadan yoqish uchun adb buyrugʻini yana bajarish kerak."
            )
            .setPositiveButton("Ha, chiqish") { _, _ ->
                try {
                    Blocker.releaseOwner(this)
                    toast("Device Owner rejimi oʻchirildi")
                } catch (e: Exception) {
                    toast("Xatolik: ${e.message}")
                }
                render()
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun askPin() {
        if (pinDialogShown) return
        pinDialogShown = true
        val input = pinInput("PIN")
        AlertDialog.Builder(this)
            .setTitle("PIN kodni kiriting")
            .setView(padded(input))
            .setCancelable(false)
            .setPositiveButton("Kirish") { _, _ ->
                pinDialogShown = false
                if (Prefs.checkPin(this, input.text.toString())) {
                    unlocked = true
                    GuardService.start(this)
                    render()
                } else {
                    toast("PIN notoʻgʻri")
                    finish()
                }
            }
            .setNegativeButton("Chiqish") { _, _ ->
                pinDialogShown = false
                finish()
            }
            .show()
    }

    private fun setPinDialog() {
        val a = pinInput("Yangi PIN (kamida 4 raqam)")
        val b = pinInput("PIN'ni takrorlang")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(a)
            addView(b)
        }
        AlertDialog.Builder(this)
            .setTitle("PIN oʻrnatish")
            .setMessage("PIN'ni unutmang! Device Owner rejimida uni tiklash uchun kompyuter va adb kerak boʻladi.")
            .setView(box)
            .setPositiveButton("Saqlash") { _, _ ->
                val p1 = a.text.toString()
                val p2 = b.text.toString()
                when {
                    p1.length < 4 -> toast("PIN kamida 4 raqamdan iborat boʻlsin")
                    p1 != p2 -> toast("PIN'lar mos kelmadi")
                    else -> {
                        Prefs.setPin(this, p1)
                        toast("PIN saqlandi")
                        render()
                    }
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    // ================= Yordamchi UI =================

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun header(t: String) {
        root.addView(TextView(this).apply {
            text = t
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1E3A8A"))
        }, lp(top = 22))
    }

    private fun note(t: String) {
        root.addView(TextView(this).apply {
            text = t
            textSize = 14f
            setTextColor(Color.parseColor("#4B5563"))
        }, lp(top = 6))
    }

    private fun statusRow(label: String, ok: Boolean, value: String) {
        root.addView(TextView(this).apply {
            text = "●  $label: $value"
            textSize = 15f
            setTextColor(Color.parseColor(if (ok) "#15803D" else "#B91C1C"))
        }, lp(top = 6))
    }

    private fun button(t: String, onClick: () -> Unit) {
        root.addView(Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        }, lp(top = 6))
    }

    private fun input(hint: String, uri: Boolean): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (uri) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        }
        root.addView(e, lp(top = 6))
        return e
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        root.addView(Switch(this).apply {
            text = label
            textSize = 15f
            isChecked = checked
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }, lp(top = 4))
    }

    private fun pinInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun padded(v: View): View = FrameLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(v)
    }

    private fun openExternal(i: Intent) {
        internalNav = true
        try {
            startActivity(i)
        } catch (_: Exception) {
            internalNav = false
            toast("Sozlamalarni ochib boʻlmadi")
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
