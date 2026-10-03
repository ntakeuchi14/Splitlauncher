package com.example.splitlauncher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * ユーザー補助サービスを自動で ON にする。
 *
 * 車載HUなどでアプリが強制停止されると、Android はそのアプリのユーザー補助を自動で OFF にする。
 * PC から一度だけ次のコマンドで権限を与えておけば、起動時に自動で ON に戻せる:
 *   adb shell pm grant com.example.splitlauncher android.permission.WRITE_SECURE_SETTINGS
 */
object A11yEnabler {

    fun canAutoEnable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** ON にする操作を行えたら true（接続完了までは少し時間がかかる） */
    fun enable(context: Context): Boolean {
        if (!canAutoEnable(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val me = ComponentName(context, DividerAccessibilityService::class.java)
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            val services = current.split(':').filter { it.isNotBlank() }.toMutableList()
            if (services.none { ComponentName.unflattenFromString(it) == me }) {
                services += me.flattenToString()
                Settings.Secure.putString(
                    resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":")
                )
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}
