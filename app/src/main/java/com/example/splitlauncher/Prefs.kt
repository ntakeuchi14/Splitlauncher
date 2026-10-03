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

    private companion object {
        const val KEY_LEFT = "left"
        const val KEY_RIGHT = "right"
        const val KEY_RATIO = "ratio"
        const val KEY_MODE = "mode"
    }
}
