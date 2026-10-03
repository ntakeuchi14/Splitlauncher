package com.example.splitlauncher.slot

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** 本体アプリにスロット番号を渡して分割起動を依頼し、すぐ終了する */
class SlotActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent()
            .setComponent(ComponentName(MAIN_PACKAGE, "$MAIN_PACKAGE.SlotEntry"))
            .putExtra(EXTRA_SLOT, BuildConfig.SLOT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_main_app_missing, Toast.LENGTH_LONG).show()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val MAIN_PACKAGE = "com.example.splitlauncher"
        const val EXTRA_SLOT = "slot"
    }
}
