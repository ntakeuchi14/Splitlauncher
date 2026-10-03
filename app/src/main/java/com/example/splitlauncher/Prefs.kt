package com.example.splitlauncher

import android.content.Context

enum class LaunchMode { SPLIT, FREEFORM }

/** 前回の選択内容を保存する */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("split_launcher", Context.MODE_PRIVATE)

    var leftComponent: String?
        get() = sp.getString(KEY_LEFT, null)
        set(v) = sp.edit().putString(KEY_LEFT, v).apply()

    var rightComponent: String?
        get() = sp.getString(KEY_RIGHT, null)
        set(v) = sp.edit().putString(KEY_RIGHT, v).apply()

    /** 左側（縦画面では上側）の割合 [%] */
    var leftRatio: Int
        get() = sp.getInt(KEY_RATIO, 50)
        set(v) = sp.edit().putInt(KEY_RATIO, v).apply()

    var mode: LaunchMode
        get() = runCatching { LaunchMode.valueOf(sp.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(LaunchMode.SPLIT)
        set(v) = sp.edit().putString(KEY_MODE, v.name).apply()

    /** 画面の向きごとに「分割の固定側が右/下になる」かどうか（自動検出結果） */
    fun isDockReversed(rotation: Int): Boolean = sp.getBoolean("$KEY_DOCK_REVERSED$rotation", false)

    fun setDockReversed(rotation: Int, reversed: Boolean) {
        sp.edit().putBoolean("$KEY_DOCK_REVERSED$rotation", reversed).apply()
    }

    /** 横画面（回転90°/270°）で左右を反転するか */
    var landscapeReversed: Boolean
        get() = isDockReversed(1) || isDockReversed(3)
        set(v) {
            setDockReversed(1, v)
            setDockReversed(3, v)
        }

    private companion object {
        const val KEY_DOCK_REVERSED = "dock_reversed_"
        const val KEY_LEFT = "left"
        const val KEY_RIGHT = "right"
        const val KEY_RATIO = "ratio"
        const val KEY_MODE = "mode"
    }
}
