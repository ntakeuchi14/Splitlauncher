package com.example.splitlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * 分割画面のディバイダーをジェスチャーでドラッグして比率を調整するサービス（任意機能）。
 * Android には他アプリの分割比率を指定する公開APIがないため、この方法で代用する。
 * 端末によってはディバイダーが 1:2 / 1:1 / 2:1 などの位置にスナップするので、指定比率に最も近い位置になる。
 */
class DividerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun scheduleDrag(leftRatio: Int, delayMs: Long) {
        if (leftRatio == 50) return // 既定の1:1なら何もしない
        handler.postDelayed({ dragDivider(leftRatio) }, delayMs)
    }

    private fun dragDivider(leftRatio: Int) {
        val size = realScreenSize()
        val w = size.x.toFloat()
        val h = size.y.toFloat()
        val landscape = w > h

        // ディバイダーは画面中央にある前提（起動直後は1:1）
        val startX = w / 2f
        val startY = h / 2f
        val (endX, endY) = if (landscape) {
            w * leftRatio / 100f to startY   // 横画面: 左右分割、ディバイダーを左右に動かす
        } else {
            startX to h * leftRatio / 100f   // 縦画面: 上下分割、ディバイダーを上下に動かす
        }

        // 1) ディバイダーを掴んで少し保持
        val holdPath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX + 1f, startY + 1f)
        }
        val hold = GestureDescription.StrokeDescription(holdPath, 0, HOLD_MS, true)

        // 2) 目標位置までドラッグして離す
        val movePath = Path().apply {
            moveTo(startX + 1f, startY + 1f)
            lineTo(endX, endY)
        }
        val move = hold.continueStroke(movePath, 0, MOVE_MS, false)

        dispatchGesture(
            GestureDescription.Builder().addStroke(hold).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dispatchGesture(GestureDescription.Builder().addStroke(move).build(), null, null)
                }
            },
            null,
        )
    }

    private fun realScreenSize(): Point {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val p = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        return p
    }

    companion object {
        private const val HOLD_MS = 300L
        private const val MOVE_MS = 500L

        @Volatile
        private var instance: DividerAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        fun requestDrag(leftRatio: Int, delayMs: Long) {
            instance?.scheduleDrag(leftRatio, delayMs)
        }
    }
}
