package dev.sanmer.pi.delegate

import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.ServiceManager
import com.android.internal.app.IAppOpsCallback
import com.android.internal.app.IAppOpsService
import dev.sanmer.pi.PackageInfoCompat.isEmpty
import dev.sanmer.pi.UserHandleCompat

class AppOpsManagerDelegate(
    private val proxy: IBinder.() -> IBinder = { this }
) {
    private val delegates = mutableListOf<AppOpsActiveCallbackDelegate>()

    private val appOpsService by lazy {
        IAppOpsService.Stub.asInterface(
            ServiceManager.getService(Context.APP_OPS_SERVICE).proxy()
        )
    }

    fun checkOpNoThrow(op: Int, uid: Int, packageName: String): Mode {
        return Mode.Unsafe(appOpsService.checkOperation(op, uid, packageName))
    }

    fun checkOpNoThrow(op: Int, packageInfo: PackageInfo): Mode {
        if (packageInfo.isEmpty) return Mode.Default

        return checkOpNoThrow(
            op = op,
            uid = packageInfo.applicationInfo!!.uid,
            packageName = packageInfo.packageName
        )
    }

    fun getPackagesForOps(ops: IntArray): List<PackageOps> {
        return appOpsService.getPackagesForOps(ops).map(AppOpsManagerDelegate::PackageOps)
    }

    fun getPackagesForOp(op: Int): List<PackageOps> {
        return getPackagesForOps(intArrayOf(op))
    }

    fun getOpsForPackage(uid: Int, packageName: String): List<OpEntry> {
        return appOpsService.getOpsForPackage(uid, packageName, null)
            ?.firstOrNull()?.ops?.map(AppOpsManagerDelegate::OpEntry) ?: emptyList()
    }

    fun getOpsForPackage(packageInfo: PackageInfo): List<OpEntry> {
        if (packageInfo.isEmpty) return emptyList()

        return getOpsForPackage(
            uid = packageInfo.applicationInfo!!.uid,
            packageName = packageInfo.packageName
        )
    }

    fun getUidOps(uid: Int): List<PackageOps> {
        return appOpsService.getUidOps(uid, null).map(AppOpsManagerDelegate::PackageOps)
    }

    fun setUidMode(op: Int, uid: Int, mode: Mode) {
        appOpsService.setUidMode(op, uid, mode.code)
    }

    fun setMode(op: Int, uid: Int, packageName: String, mode: Mode) {
        appOpsService.setMode(op, uid, packageName, mode.code)
    }

    fun setMode(op: Int, packageInfo: PackageInfo, mode: Mode) {
        if (packageInfo.isEmpty) return

        setMode(
            op = op,
            uid = packageInfo.applicationInfo!!.uid,
            packageName = packageInfo.packageName,
            mode = mode
        )
    }

    fun resetAllModes() {
        val userId = UserHandleCompat.myUserId()
        appOpsService.resetAllModes(userId, null)
    }

    fun startWatchingMode(op: Int, packageName: String?, callback: AppOpsCallback) {
        val delegate = AppOpsActiveCallbackDelegate(callback)
        appOpsService.startWatchingMode(op, packageName, delegate)
        delegates.add(delegate)
    }

    fun stopWatchingMode(callback: AppOpsCallback) {
        val delegate = delegates.find { it.callback == callback }
        if (delegate != null) {
            appOpsService.stopWatchingMode(delegate)
        }
    }

    interface AppOpsCallback {
        fun opChanged(op: Int, uid: Int, packageName: String) {}
    }

    internal class AppOpsActiveCallbackDelegate(
        val callback: AppOpsCallback
    ) : IAppOpsCallback.Stub() {
        override fun opChanged(op: Int, uid: Int, packageName: String) {
            callback.opChanged(op, uid, packageName)
        }

        override fun opChanged(op: Int, uid: Int, packageName: String, persistentDeviceId: String?) {
            callback.opChanged(op, uid, packageName)
        }

    }

    enum class Mode(internal val code: Int) {
        Allow(MODE_ALLOWED),
        Deny(MODE_DENY),
        Ignore(MODE_IGNORED),
        Default(MODE_DEFAULT),
        Foreground(MODE_FOREGROUND);

        val isAllowed inline get() = this == Allow
        val isDenied inline get() = this == Deny
        val isIgnored inline get() = this == Ignore
        val isDefaulted inline get() = this == Default
        val isForegrounded inline get() = this == Foreground

        internal companion object Unsafe {
            operator fun invoke(value: Int) = entries.first { it.code == value }
        }
    }

    class OpEntry internal constructor(
        private val original: AppOpsManagerHidden.OpEntry
    ) {
        val op = original.op
        val opStr = original.opStr
        val mode = original.mode
        val time = original.time
    }

    class PackageOps internal constructor(
        private val original: AppOpsManagerHidden.PackageOps
    ) {
        val packageName = original.packageName
        val uid = original.uid
        val ops by lazy { original.ops.map { OpEntry(it) } }
    }

    companion object Default {
        val MODE_ALLOWED get() = AppOpsManager.MODE_ALLOWED

        val MODE_IGNORED get() = AppOpsManager.MODE_IGNORED

        val MODE_DENY get() = AppOpsManager.MODE_ERRORED

        val MODE_DEFAULT get() = AppOpsManager.MODE_DEFAULT

        val MODE_FOREGROUND get() = AppOpsManager.MODE_FOREGROUND

        val OP_NONE get() = AppOpsManagerHidden.OP_NONE

        val OP_VIBRATE get() = AppOpsManagerHidden.OP_VIBRATE

        val OP_REQUEST_INSTALL_PACKAGES get() = AppOpsManagerHidden.OP_REQUEST_INSTALL_PACKAGES

        val OP_REQUEST_DELETE_PACKAGES get() = AppOpsManagerHidden.OP_REQUEST_DELETE_PACKAGES

        fun opToName(op: Int): String {
            return AppOpsManagerHidden.opToName(op)
        }

        fun opToPermission(op: Int): String {
            return AppOpsManagerHidden.opToPermission(op)
        }
    }
}