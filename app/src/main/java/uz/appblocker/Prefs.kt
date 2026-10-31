package uz.appblocker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Bloklash qoidasi: paket nomi (com.instagram.android) va Play Market'dagi nomi (Instagram). */
data class Rule(val pkg: String, val name: String)

object Prefs {
    private const val FILE = "blocker"
    private const val KEY_RULES = "rules"
    private const val KEY_PIN = "pin_hash"

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun rules(ctx: Context): List<Rule> {
        val raw = sp(ctx).getString(KEY_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rule(o.getString("pkg"), o.optString("name", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRules(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("name", it.name)) }
        sp(ctx).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun addRule(ctx: Context, rule: Rule) {
        saveRules(ctx, rules(ctx).filterNot { it.pkg == rule.pkg } + rule)
    }

    fun removeRule(ctx: Context, pkg: String) {
        saveRules(ctx, rules(ctx).filterNot { it.pkg == pkg })
    }

    fun ruleFor(ctx: Context, pkg: String): Rule? = rules(ctx).firstOrNull { it.pkg == pkg }

    // ---- PIN ----

    fun hasPin(ctx: Context): Boolean = sp(ctx).getString(KEY_PIN, null) != null

    fun setPin(ctx: Context, pin: String?) {
        val e = sp(ctx).edit()
        if (pin.isNullOrEmpty()) e.remove(KEY_PIN) else e.putString(KEY_PIN, hash(pin))
        e.apply()
    }

    fun checkPin(ctx: Context, pin: String): Boolean =
        sp(ctx).getString(KEY_PIN, null) == hash(pin)

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("uz.appblocker:$s").toByteArray())
            .joinToString("") { "%02x".format(it) }
}
