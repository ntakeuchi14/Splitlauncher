package com.example.splitlauncher

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import java.lang.reflect.Method

/**
 * フリーフォームウィンドウで起動するための ActivityOptions を作る。
 *
 * Android 10 などでは起動位置（setLaunchBounds）を指定するだけではフリーフォームにならず、
 * 非公開APIの setLaunchWindowingMode(WINDOWING_MODE_FREEFORM) も必要になる。
 * 非公開APIの利用制限は、Android 9〜10 で有効な方法で解除を試みる（失敗しても通常どおり続行）。
 */
object Freeform {

    private const val WINDOWING_MODE_FREEFORM = 5

    @Volatile
    private var hiddenApiExempted = false

    fun options(bounds: Rect): Bundle {
        val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        setFreeformWindowingMode(options)
        return options.toBundle()
    }

    private fun setFreeformWindowingMode(options: ActivityOptions) {
        exemptHiddenApis()
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }
    }

    /** VMRuntime.setHiddenApiExemptions を反射経由で呼び、非公開APIの制限を外す（Android 9〜10） */
    private fun exemptHiddenApis() {
        if (hiddenApiExempted || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntime, "getRuntime", null) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L") as Any)
            hiddenApiExempted = true
        }
    }
}
