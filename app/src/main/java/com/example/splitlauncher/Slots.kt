package com.example.splitlauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** アプリ一覧に「分割N」として出す組み合わせ */
data class Slot(
    val left: ComponentName,
    val right: ComponentName,
    val ratio: Int,
    val mode: LaunchMode,
)

/**
 * スロット（分割1〜5）の保存と、アプリ一覧への表示/非表示の切り替え。
 * 表示はマニフェストの activity-alias（.Slot1〜.Slot5）を有効/無効にすることで行う。
 */
class SlotStore(private val context: Context) {

    private val sp = context.getSharedPreferences("split_launcher_slots", Context.MODE_PRIVATE)

    fun get(index: Int): Slot? {
        val left = sp.getString("$index.left", null)?.let(ComponentName::unflattenFromString) ?: return null
        val right = sp.getString("$index.right", null)?.let(ComponentName::unflattenFromString) ?: return null
        val mode = runCatching { LaunchMode.valueOf(sp.getString("$index.mode", null) ?: "") }
            .getOrDefault(LaunchMode.SPLIT)
        return Slot(left, right, sp.getInt("$index.ratio", 50), mode)
    }

    fun save(index: Int, slot: Slot) {
        sp.edit()
            .putString("$index.left", slot.left.flattenToString())
            .putString("$index.right", slot.right.flattenToString())
            .putInt("$index.ratio", slot.ratio)
            .putString("$index.mode", slot.mode.name)
            .apply()
        setVisibleInLauncher(index, true)
    }

    fun clear(index: Int) {
        sp.edit()
            .remove("$index.left")
            .remove("$index.right")
            .remove("$index.ratio")
            .remove("$index.mode")
            .apply()
        setVisibleInLauncher(index, false)
    }

    private fun setVisibleInLauncher(index: Int, visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            aliasComponent(context, index),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        const val COUNT = 5
        private const val ALIAS_PREFIX = "com.example.splitlauncher.Slot"

        fun aliasComponent(context: Context, index: Int) =
            ComponentName(context.packageName, "$ALIAS_PREFIX$index")

        /** 起動元のコンポーネント名（.SlotN）からスロット番号を得る。スロット経由でなければ null */
        fun indexOf(component: ComponentName?): Int? =
            component?.className
                ?.takeIf { it.startsWith(ALIAS_PREFIX) }
                ?.removePrefix(ALIAS_PREFIX)
                ?.toIntOrNull()
                ?.takeIf { it in 1..COUNT }
    }
}
