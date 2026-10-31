package uz.appblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings

/** Bloklashning umumiy mantiqi: Accessibility, xizmat va ekranlar shu yerdan foydalanadi. */
object Blocker {
    const val PLAY_STORE = "com.android.vending"
    const val CH_GUARD = "guard"
    const val CH_ALERTS = "alerts"

    /** Bloklab bo'lmaydigan tizim ilovalari (aks holda telefon qulflanib qolishi mumkin). */
    private val PROTECTED = setOf(
        PLAY_STORE,
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.phone",
        "com.google.android.dialer",
        "com.android.dialer",
    )

    /** Device Owner rejimidagi qo'shimcha cheklovlar (ixtiyoriy). */
    val OWNER_RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET to "Zavod sozlamalariga qaytarishni taqiqlash",
        UserManager.DISALLOW_SAFE_BOOT to "Xavfsiz rejim (Safe mode)ni taqiqlash",
        UserManager.DISALLOW_APPS_CONTROL to "Sozlamalarda ilovalarni oʻchirish, majburan toʻxtatish va maʼlumotini tozalashni taqiqlash",
    )

    private val recent = HashMap<String, Long>()

    // ---------- Holat ----------

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, BlockerAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    @Suppress("DEPRECATION")
    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    fun labelOf(ctx: Context, pkg: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Bosh ekran (launcher) ilovalarini ham himoyalangan deb hisoblaymiz. */
    @Suppress("DEPRECATION")
    fun isProtected(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg in PROTECTED) return true
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(home, 0)
            .any { it.activityInfo?.packageName == pkg }
    }

    /** Play Market havolasi, market:// havola yoki oddiy paket nomidan paket nomini ajratadi. */
    fun parsePackage(input: String): String? {
        val t = input.trim()
        val fromUrl = Regex("[?&]id=([A-Za-z0-9_.]+)").find(t)?.groupValues?.get(1)
        val p = fromUrl ?: t.removePrefix("package:")
        return if (Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(p)) p else null
    }

    // ---------- Bloklash ----------

    /**
     * Bloklangan ilova o'rnatilganda (yoki o'rnatilgan holda topilganda) chaqiriladi.
     * Device Owner: darhol muzlatadi va jimgina o'chiradi.
     * Oddiy rejim: o'chirish oynasini ochadi va bildirishnoma ko'rsatadi.
     */
    @Synchronized
    fun onPackageAdded(ctx: Context, pkg: String, interactive: Boolean) {
        val rule = Prefs.ruleFor(ctx, pkg) ?: return
        if (!isInstalled(ctx, pkg)) return
        val now = SystemClock.elapsedRealtime()
        val last = recent[pkg]
        if (last != null && now - last < 5_000) return
        recent[pkg] = now

        if (isDeviceOwner(ctx)) {
            try {
                dpm(ctx).setPackagesSuspended(admin(ctx), arrayOf(pkg), true)
            } catch (_: Exception) {
            }
            uninstallSilently(ctx, pkg)
        } else {
            val del = uninstallIntent(pkg)
            val pi = PendingIntent.getActivity(
                ctx, pkg.hashCode(), del,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notify(
                ctx, pkg.hashCode(),
                "Bloklangan ilova oʻrnatildi",
                "«${displayName(rule)}» — oʻchirish uchun bosing.",
                pi
            )
            if (interactive) {
                try {
                    ctx.startActivity(del)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Ro'yxatdagi, lekin telefonda o'rnatilgan barcha ilovalarni tekshiradi. */
    fun enforceInstalled(ctx: Context, interactive: Boolean) {
        Prefs.rules(ctx).forEach { if (isInstalled(ctx, it.pkg)) onPackageAdded(ctx, it.pkg, interactive) }
    }

    fun displayName(rule: Rule): String = rule.name.ifBlank { rule.pkg }

    private fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Device Owner ilovasi uchun o'chirish tasdiqsiz bajariladi. */
    private fun uninstallSilently(ctx: Context, pkg: String) {
        val intent = Intent(ctx, UninstallResultReceiver::class.java)
            .putExtra(UninstallResultReceiver.EXTRA_PKG, pkg)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, pkg.hashCode(), intent, flags)
        try {
            ctx.packageManager.packageInstaller.uninstall(pkg, pi.intentSender)
        } catch (_: Exception) {
        }
    }

    /** Qoida o'chirilganda muzlatilgan ilovani qayta ishlatishga ruxsat beradi. */
    fun unsuspend(ctx: Context, pkg: String) {
        if (!isDeviceOwner(ctx)) return
        try {
            dpm(ctx).setPackagesSuspended(admin(ctx), arrayOf(pkg), false)
        } catch (_: Exception) {
        }
    }

    // ---------- Device Owner ----------

    fun applyOwnerPolicies(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        try {
            dpm(ctx).setUninstallBlocked(admin(ctx), ctx.packageName, true)
        } catch (_: Exception) {
        }
    }

    fun hasRestriction(ctx: Context, key: String): Boolean = try {
        dpm(ctx).getUserRestrictions(admin(ctx)).getBoolean(key)
    } catch (_: Exception) {
        false
    }

    fun setRestriction(ctx: Context, key: String, on: Boolean) {
        val d = dpm(ctx)
        val a = admin(ctx)
        try {
            if (on) d.addUserRestriction(a, key) else d.clearUserRestriction(a, key)
        } catch (_: Exception) {
        }
    }

    /** Barcha cheklovlarni olib tashlab, Device Owner rejimidan chiqadi (ilovani o'chirish mumkin bo'ladi). */
    @Suppress("DEPRECATION")
    fun releaseOwner(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val d = dpm(ctx)
        val a = admin(ctx)
        OWNER_RESTRICTIONS.forEach { (key, _) ->
            try {
                d.clearUserRestriction(a, key)
            } catch (_: Exception) {
            }
        }
        try {
            d.setPackagesSuspended(a, Prefs.rules(ctx).map { it.pkg }.toTypedArray(), false)
        } catch (_: Exception) {
        }
        try {
            d.setUninstallBlocked(a, ctx.packageName, false)
        } catch (_: Exception) {
        }
        d.clearDeviceOwnerApp(ctx.packageName)
    }

    // ---------- Bildirishnomalar ----------

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_GUARD) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_GUARD, ctx.getString(R.string.channel_guard), NotificationManager.IMPORTANCE_MIN)
            )
        }
        if (nm.getNotificationChannel(CH_ALERTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERTS, ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String, pi: PendingIntent?) {
        ensureChannels(ctx)
        val b = Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }
}
