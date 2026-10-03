package com.example.splitlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast

/**
 * 2つのアプリを並べて起動する透明な中継Activity。
 * MainActivity の「起動」ボタンとホーム画面ショートカットの両方から呼ばれる。
 */
class LaunchPairActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val left = intent.getStringExtra(EXTRA_LEFT)?.let(ComponentName::unflattenFromString)
        val right = intent.getStringExtra(EXTRA_RIGHT)?.let(ComponentName::unflattenFromString)
        val ratio = intent.getIntExtra(EXTRA_RATIO, 50).coerceIn(MIN_RATIO, MAX_RATIO)
        val mode = runCatching { LaunchMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }
            .getOrDefault(LaunchMode.SPLIT)

        if (left == null || right == null) {
            fail(getString(R.string.error_no_app))
            return
        }

        try {
            when (mode) {
                LaunchMode.SPLIT -> launchSplit(left, right, ratio)
                LaunchMode.FREEFORM -> launchFreeform(left, right, ratio)
            }
        } catch (e: ActivityNotFoundException) {
            fail(getString(R.string.error_app_not_found))
        } catch (e: SecurityException) {
            fail(getString(R.string.error_launch_failed, e.message ?: ""))
        }
    }

    /**
     * 標準の分割画面モード。
     * 1) 右側アプリを FLAG_ACTIVITY_LAUNCH_ADJACENT で起動 → このActivityと右アプリで分割画面になる
     * 2) このActivityの側（左/上）に左側アプリを起動して置き換える
     * 3) 比率は公開APIで指定できないため、ユーザー補助サービスが有効ならディバイダーをドラッグして調整
     */
    private fun launchSplit(left: ComponentName, right: ComponentName, ratio: Int) {
        startActivity(appIntent(right).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
        handler.postDelayed({
            runCatching { startActivity(appIntent(left)) }
                .onFailure { Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show() }
            DividerAccessibilityService.requestDrag(ratio, DIVIDER_DRAG_DELAY_MS)
            finishQuietly()
        }, SECOND_LAUNCH_DELAY_MS)
    }

    /**
     * フリーフォームウィンドウモード。
     * 端末がフリーフォームに対応していれば、起動位置・サイズを指定できるので比率を正確に反映できる。
     */
    private fun launchFreeform(left: ComponentName, right: ComponentName, ratio: Int) {
        val screen = screenBounds()
        val splitX = screen.left + screen.width() * ratio / 100
        val leftBounds = Rect(screen.left, screen.top, splitX, screen.bottom)
        val rightBounds = Rect(splitX, screen.top, screen.right, screen.bottom)

        startActivity(appIntent(left), ActivityOptions.makeBasic().setLaunchBounds(leftBounds).toBundle())
        handler.postDelayed({
            runCatching {
                startActivity(appIntent(right), ActivityOptions.makeBasic().setLaunchBounds(rightBounds).toBundle())
            }.onFailure { Toast.makeText(this, R.string.error_app_not_found, Toast.LENGTH_SHORT).show() }
            finishQuietly()
        }, SECOND_LAUNCH_DELAY_MS)
    }

    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.maximumWindowMetrics.bounds)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }

    private fun appIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finishQuietly()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val MIN_RATIO = 20
        const val MAX_RATIO = 80

        private const val EXTRA_LEFT = "left"
        private const val EXTRA_RIGHT = "right"
        private const val EXTRA_RATIO = "ratio"
        private const val EXTRA_MODE = "mode"

        private const val SECOND_LAUNCH_DELAY_MS = 500L
        private const val DIVIDER_DRAG_DELAY_MS = 1200L

        fun createIntent(
            context: Context,
            left: ComponentName,
            right: ComponentName,
            ratio: Int,
            mode: LaunchMode,
        ): Intent = Intent(context, LaunchPairActivity::class.java)
            .setAction(Intent.ACTION_VIEW) // ショートカットにはactionが必須
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_LEFT, left.flattenToString())
            .putExtra(EXTRA_RIGHT, right.flattenToString())
            .putExtra(EXTRA_RATIO, ratio)
            .putExtra(EXTRA_MODE, mode.name)
    }
}
