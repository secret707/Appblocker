package uz.appblocker

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Device Owner / Device Admin qabul qiluvchisi. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Blocker.applyOwnerPolicies(context)
        GuardService.start(context)
    }
}

/** Telefon yoqilganda va ilova yangilanganda himoyani qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Blocker.applyOwnerPolicies(context)
        GuardService.start(context)
    }
}

/**
 * PIN unutilsa — tiklash. Faqat kompyuterdan adb orqali ishga tushirish mumkin
 * (manifestda DUMP ruxsati bilan himoyalangan, oddiy ilovalarda bu ruxsat bo'lmaydi):
 *   adb shell am broadcast -a uz.appblocker.RESET_PIN -n uz.appblocker/.ResetReceiver
 */
class ResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.setPin(context, null)
        try {
            resultData = "PIN olib tashlandi"
        } catch (_: Exception) {
        }
    }
}

/** Jimgina o'chirish natijasi. */
class UninstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val name = Prefs.ruleFor(context, pkg)?.let { Blocker.displayName(it) } ?: pkg
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Blocker.notify(
                context, pkg.hashCode(),
                "Bloklangan ilova oʻchirildi",
                "«$name» oʻrnatilgan edi — avtomatik oʻchirildi.",
                null
            )

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }

            else -> Blocker.notify(
                context, pkg.hashCode(),
                "«$name» muzlatildi",
                "Ilovani avtomatik oʻchirib boʻlmadi, lekin u ishlamaydi.",
                null
            )
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
