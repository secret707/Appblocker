package uz.appblocker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Doimiy ishlaydigan xizmat: yangi ilova o'rnatilganini (Play Market, brauzer yoki APK fayl orqali)
 * darhol sezadi va bloklangan bo'lsa choralar ko'radi.
 */
class GuardService : Service() {

    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val pkg = i.data?.schemeSpecificPart ?: return
                Blocker.onPackageAdded(c.applicationContext, pkg, interactive = true)
            }
        }
        val f = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(r, f)
        }
        receiver = r
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        Blocker.applyOwnerPolicies(this)
        Blocker.enforceInstalled(applicationContext, interactive = false)
        return START_STICKY
    }

    private fun goForeground() {
        Blocker.ensureChannels(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, Blocker.CH_GUARD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.guard_title))
            .setContentText(getString(R.string.guard_text))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        receiver = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            if (Prefs.rules(ctx).isEmpty()) {
                stop(ctx)
                return
            }
            try {
                ctx.startForegroundService(Intent(ctx, GuardService::class.java))
            } catch (_: Exception) {
                // Android 12+ fonda ishga tushirishni cheklashi mumkin — keyingi safar ilova ochilganda ishga tushadi.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GuardService::class.java))
        }
    }
}
