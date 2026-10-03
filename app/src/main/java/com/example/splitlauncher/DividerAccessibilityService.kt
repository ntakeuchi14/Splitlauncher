package com.example.splitlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * 分割画面の操作を担当するユーザー補助サービス。
 *
 * - Android 7〜12: 「分割画面切替」(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) で分割を開始する（必須）
 * - 全バージョン: 分割後にディバイダーをドラッグして比率を調整する
 *
 * 画面の文字や内容は読み取らず、ウィンドウの種類と位置（ディバイダーの場所）だけを参照する。
 */
class DividerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    // ---------------------------------------------------------------------
    // Android 7〜12 用: 分割画面の開始手順
    // ---------------------------------------------------------------------

    /**
     * @param allowRetry 配置が逆だった場合に1回だけ順序を入れ替えてやり直すためのフラグ
     */
    private fun runLegacySplit(
        left: ComponentName,
        right: ComponentName,
        ratio: Int,
        allowRetry: Boolean = true,
    ) {
        handler.removeCallbacksAndMessages(null)

        // 固定側（最初に起動したアプリが入る側）は端末・画面の向きで左右（上下）が変わる。
        // 前回の検出結果に従い、固定側が右/下になる向きなら右アプリを先に起動する。
        val rotation = currentRotation()
        val reversed = prefs.isDockReversed(rotation)
        val first = if (reversed) right else left
        val second = if (reversed) left else right

        // 0) すでに分割画面なら一度解除してから始める（切替操作は ON/OFF のトグルなので）
        val preDelay = if (findDivider() != null) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            STEP_DELAY_MS
        } else 0L

        handler.postDelayed({
            // 1) 固定側に入れるアプリを全画面で起動
            if (!startApp(first, adjacent = false)) return@postDelayed

            handler.postDelayed({
                // 2) 分割画面切替 → 前面のアプリが固定側に入る
                toggleSplit(retry = true) {
                    // 2.5) 実際に固定側が左/上か右/下かを検出し、想定と逆なら覚えてやり直す
                    val firstAtStart = detectIsAtStartSide(first.packageName)
                    if (firstAtStart != null && firstAtStart == reversed) {
                        prefs.setDockReversed(rotation, !reversed)
                        if (allowRetry) {
                            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) // 分割を解除
                            handler.postDelayed({
                                runLegacySplit(left, right, ratio, allowRetry = false)
                            }, STEP_DELAY_MS)
                            return@toggleSplit
                        }
                    }

                    // 3) もう一方の側（アプリ一覧が出ている側）に2つ目のアプリを起動
                    //    Android 9 以前は分割中に LAUNCH_ADJACENT を付けると、フォーカスがアプリ一覧側にあるため
                    //    固定側に起動されて1つ目のアプリが置き換わってしまう。通常起動ならアプリ一覧側に入る。
                    if (!startApp(second, adjacent = false)) return@toggleSplit
                    // 4) 比率を調整
                    scheduleDrag(ratio, STEP_DELAY_MS)
                }
            }, APP_START_DELAY_MS)
        }, preDelay)
    }

    /**
     * 指定パッケージのウィンドウが、ディバイダーより左（左右分割時）または上（上下分割時）にあるか。
     * 判定できなければ null。
     */
    private fun detectIsAtStartSide(packageName: String): Boolean? = runCatching {
        val divider = findDivider() ?: return@runCatching null
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        // まずアプリ名で探し、取れなければ「フォーカスの無いアプリウィンドウ」を固定側とみなす
        // （分割直後はもう一方の側のアプリ一覧にフォーカスがあるため）
        val window = appWindows.firstOrNull { it.root?.packageName?.toString() == packageName }
            ?: appWindows.singleOrNull { !it.isFocused && !it.isActive }
            ?: return@runCatching null
        val bounds = Rect().also { window.getBoundsInScreen(it) }
        val leftRightSplit = divider.height() > divider.width()
        if (leftRightSplit) bounds.centerX() < divider.centerX() else bounds.centerY() < divider.centerY()
    }.getOrNull()

    private fun currentRotation(): Int {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
    }

    private fun toggleSplit(retry: Boolean, onSplit: () -> Unit) {
        val accepted = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        handler.postDelayed({
            when {
                findDivider() != null -> onSplit()
                // 受け付けられたがディバイダーを検出できない端末もあるため、そのまま続行
                // （ここで再度切り替えると分割が解除されてしまう）
                accepted -> onSplit()
                // 拒否された＝起動アニメーション中などの可能性。1回だけ再試行
                retry -> toggleSplit(retry = false, onSplit = onSplit)
                else -> toast(R.string.error_split_failed)
            }
        }, STEP_DELAY_MS)
    }

    private fun startApp(component: ComponentName, adjacent: Boolean): Boolean {
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        if (adjacent) flags = flags or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(flags)
        return runCatching { startActivity(intent) }
            .onFailure { toast(R.string.error_app_not_found) }
            .isSuccess
    }

    // ---------------------------------------------------------------------
    // ディバイダーのドラッグで比率調整
    // ---------------------------------------------------------------------

    private fun scheduleDrag(leftRatio: Int, delayMs: Long) {
        if (leftRatio == 50) return // 既定の1:1なら何もしない
        handler.postDelayed({ dragDivider(leftRatio) }, delayMs)
    }

    private fun dragDivider(leftRatio: Int) {
        val screen = realScreenSize()
        val divider = findDivider()

        // ディバイダーの中心（見つからなければ画面中央と仮定）
        val startX = divider?.exactCenterX() ?: (screen.x / 2f)
        val startY = divider?.exactCenterY() ?: (screen.y / 2f)

        // ディバイダーが縦長なら左右分割、横長なら上下分割
        val leftRightSplit = if (divider != null) {
            divider.height() > divider.width()
        } else {
            screen.x > screen.y
        }

        val endX = if (leftRightSplit) screen.x * leftRatio / 100f else startX
        val endY = if (leftRightSplit) startY else screen.y * leftRatio / 100f

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

    /** 分割画面のディバイダーの画面上の位置。分割していなければ null */
    private fun findDivider(): Rect? = runCatching {
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
            ?.let { w -> Rect().also { w.getBoundsInScreen(it) } }
    }.getOrNull()

    private fun realScreenSize(): Point {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val p = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        return p
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val HOLD_MS = 300L
        private const val MOVE_MS = 500L

        /** アプリ起動後、切替操作までの待ち時間（起動アニメーション完了待ち） */
        private const val APP_START_DELAY_MS = 1200L

        /** 分割切替・2つ目のアプリ起動などの各ステップ間の待ち時間 */
        private const val STEP_DELAY_MS = 900L

        @Volatile
        private var instance: DividerAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        fun requestDrag(leftRatio: Int, delayMs: Long) {
            instance?.scheduleDrag(leftRatio, delayMs)
        }

        fun startLegacySplit(left: ComponentName, right: ComponentName, ratio: Int) {
            instance?.runLegacySplit(left, right, ratio)
        }
    }
}
