package com.example.splitlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var leftSpinner: Spinner
    private lateinit var rightSpinner: Spinner
    private lateinit var ratioSeek: SeekBar
    private lateinit var ratioText: TextView
    private lateinit var previewLeft: View
    private lateinit var previewRight: View
    private lateinit var modeGroup: RadioGroup
    private lateinit var freeformRadio: RadioButton
    private lateinit var modeNote: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var a11yButton: Button
    private lateinit var launchButton: Button
    private lateinit var shortcutButton: Button
    private lateinit var progress: ProgressBar

    private lateinit var slotStore: SlotStore
    private lateinit var slotContainer: LinearLayout

    private var adapter: AppSpinnerAdapter? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        slotStore = SlotStore(this)

        leftSpinner = findViewById(R.id.leftSpinner)
        rightSpinner = findViewById(R.id.rightSpinner)
        ratioSeek = findViewById(R.id.ratioSeek)
        ratioText = findViewById(R.id.ratioText)
        previewLeft = findViewById(R.id.previewLeft)
        previewRight = findViewById(R.id.previewRight)
        modeGroup = findViewById(R.id.modeGroup)
        freeformRadio = findViewById(R.id.modeFreeform)
        modeNote = findViewById(R.id.modeNote)
        a11yStatus = findViewById(R.id.a11yStatus)
        a11yButton = findViewById(R.id.a11yButton)
        launchButton = findViewById(R.id.launchButton)
        shortcutButton = findViewById(R.id.shortcutButton)
        progress = findViewById(R.id.progress)

        setupRatio()
        setupMode()

        findViewById<Button>(R.id.swapButton).setOnClickListener { swap() }

        // Android 12 以下の分割起動でのみ使う設定
        val reverseCheck = findViewById<CheckBox>(R.id.landscapeReverseCheck)
        reverseCheck.visibility =
            if (android.os.Build.VERSION.SDK_INT < LaunchPairActivity.LEGACY_SPLIT_MAX_SDK_EXCLUSIVE) View.VISIBLE else View.GONE
        reverseCheck.isChecked = prefs.landscapeReversed
        reverseCheck.setOnCheckedChangeListener { _, checked -> prefs.landscapeReversed = checked }
        a11yButton.setOnClickListener {
            // 別タスクで開く（この画面は離れると閉じるため、同じタスクだと設定画面も一緒に閉じてしまう）
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        launchButton.setOnClickListener { launch() }
        shortcutButton.setOnClickListener { createShortcut() }
        // ホーム画面のない端末（車載HUなど）ではショートカットボタンを隠す
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            shortcutButton.visibility = View.GONE
        }

        slotContainer = findViewById(R.id.slotContainer)
        renderSlots()

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        updateA11yStatus()
        // 自動判定で更新されている場合があるので表示を合わせる
        findViewById<CheckBox>(R.id.landscapeReverseCheck).let {
            if (it.isChecked != prefs.landscapeReversed) it.isChecked = prefs.landscapeReversed
        }
    }

    /**
     * 画面から離れたらタスクごと閉じる。
     * 車載HUなどのランチャーは、アプリのタスクが残っていると「分割N」をタップしても
     * そのタスク（この設定画面）を前面に出すだけで、スロットの起動が行われないことがあるため。
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---- アプリ一覧 ----

    private fun loadApps() {
        setUiEnabled(false)
        progress.visibility = View.VISIBLE
        executor.execute {
            val apps = AppRepository.loadLaunchableApps(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                onAppsLoaded(apps)
            }
        }
    }

    private fun onAppsLoaded(apps: List<AppInfo>) {
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.error_no_apps, Toast.LENGTH_LONG).show()
            return
        }
        val a = AppSpinnerAdapter(this, apps)
        adapter = a
        leftSpinner.adapter = a
        rightSpinner.adapter = a

        leftSpinner.setSelection(a.indexOf(prefs.leftComponent).takeIf { it >= 0 } ?: 0)
        rightSpinner.setSelection(
            a.indexOf(prefs.rightComponent).takeIf { it >= 0 } ?: minOf(1, apps.lastIndex)
        )

        val saveListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveSelection()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        leftSpinner.onItemSelectedListener = saveListener
        rightSpinner.onItemSelectedListener = saveListener
        setUiEnabled(true)
        renderSlots()
    }

    private fun selectedLeft(): AppInfo? = adapter?.getItem(leftSpinner.selectedItemPosition)
    private fun selectedRight(): AppInfo? = adapter?.getItem(rightSpinner.selectedItemPosition)

    private fun saveSelection() {
        selectedLeft()?.let { prefs.leftComponent = it.component.flattenToString() }
        selectedRight()?.let { prefs.rightComponent = it.component.flattenToString() }
    }

    private fun swap() {
        val l = leftSpinner.selectedItemPosition
        leftSpinner.setSelection(rightSpinner.selectedItemPosition)
        rightSpinner.setSelection(l)
        saveSelection()
    }

    // ---- 分割比率 ----

    private fun setupRatio() {
        // SeekBar: 0..12 → 20%..80%（5%刻み）
        ratioSeek.max = (LaunchPairActivity.MAX_RATIO - LaunchPairActivity.MIN_RATIO) / RATIO_STEP
        ratioSeek.progress = (prefs.leftRatio - LaunchPairActivity.MIN_RATIO) / RATIO_STEP
        updateRatioViews(currentRatio())
        ratioSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRatioViews(currentRatio())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.leftRatio = currentRatio()
            }
        })
    }

    private fun currentRatio(): Int = LaunchPairActivity.MIN_RATIO + ratioSeek.progress * RATIO_STEP

    private fun updateRatioViews(ratio: Int) {
        ratioText.text = getString(R.string.ratio_format, ratio, 100 - ratio)
        (previewLeft.layoutParams as LinearLayout.LayoutParams).weight = ratio.toFloat()
        (previewRight.layoutParams as LinearLayout.LayoutParams).weight = (100 - ratio).toFloat()
        previewLeft.requestLayout()
    }

    // ---- 起動モード ----

    private fun setupMode() {
        val freeformSupported = isFreeformSupported()
        freeformRadio.isEnabled = freeformSupported
        val mode = if (prefs.mode == LaunchMode.FREEFORM && freeformSupported) LaunchMode.FREEFORM else LaunchMode.SPLIT
        modeGroup.check(if (mode == LaunchMode.FREEFORM) R.id.modeFreeform else R.id.modeSplit)
        updateModeNote()
        modeGroup.setOnCheckedChangeListener { _, _ ->
            prefs.mode = currentMode()
            updateModeNote()
        }
    }

    private fun currentMode(): LaunchMode =
        if (modeGroup.checkedRadioButtonId == R.id.modeFreeform) LaunchMode.FREEFORM else LaunchMode.SPLIT

    private fun updateModeNote() {
        modeNote.setText(
            when {
                currentMode() == LaunchMode.FREEFORM -> R.string.note_freeform
                !isFreeformSupported() -> R.string.note_split_no_freeform
                else -> R.string.note_split
            }
        )
        updateA11yStatus()
    }

    private fun isFreeformSupported(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) != 0

    private fun updateA11yStatus() {
        val splitMode = currentMode() == LaunchMode.SPLIT
        a11yStatus.visibility = if (splitMode) View.VISIBLE else View.GONE
        a11yButton.visibility = if (splitMode && !DividerAccessibilityService.isRunning) View.VISIBLE else View.GONE
        val legacy = android.os.Build.VERSION.SDK_INT < LaunchPairActivity.LEGACY_SPLIT_MAX_SDK_EXCLUSIVE
        a11yStatus.setText(
            when {
                DividerAccessibilityService.isRunning -> R.string.a11y_on
                legacy -> R.string.a11y_off_required
                else -> R.string.a11y_off
            }
        )
    }

    // ---- 起動 / ショートカット ----

    private fun buildLaunchIntent(): Intent? {
        val l = selectedLeft()
        val r = selectedRight()
        if (l == null || r == null) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
            return null
        }
        if (l.component == r.component) {
            Toast.makeText(this, R.string.error_same_app, Toast.LENGTH_SHORT).show()
            return null
        }
        return LaunchPairActivity.createIntent(this, l.component, r.component, currentRatio(), currentMode())
    }

    private fun launch() {
        val intent = buildLaunchIntent() ?: return
        prefs.leftRatio = currentRatio()
        startActivity(intent)
    }

    private fun createShortcut() {
        val intent = buildLaunchIntent() ?: return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.error_shortcut_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val l = selectedLeft()!!
        val r = selectedRight()!!
        val ratio = currentRatio()
        val id = "pair_${l.component.flattenToShortString()}_${r.component.flattenToShortString()}_${ratio}_${currentMode()}"
        val shortcut = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel("${l.label} | ${r.label}")
            .setLongLabel(getString(R.string.shortcut_long_label, l.label, r.label, ratio, 100 - ratio))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    // ---- スロット（アプリ一覧の「分割N」） ----

    private fun renderSlots() {
        slotContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (index in 1..SlotStore.COUNT) {
            val row = inflater.inflate(R.layout.item_slot, slotContainer, false)
            val slot = slotStore.get(index)

            row.findViewById<ImageView>(R.id.slotIcon).setImageResource(SLOT_ICONS[index - 1])
            row.findViewById<TextView>(R.id.slotTitle).text = getString(R.string.slot_title, index)
            row.findViewById<TextView>(R.id.slotSummary).text = if (slot == null) {
                getString(R.string.slot_empty)
            } else {
                getString(
                    R.string.slot_summary,
                    adapter?.labelOf(slot.left) ?: slot.left.packageName,
                    adapter?.labelOf(slot.right) ?: slot.right.packageName,
                    slot.ratio,
                    100 - slot.ratio,
                    getString(if (slot.mode == LaunchMode.FREEFORM) R.string.slot_mode_freeform else R.string.slot_mode_split),
                )
            }

            row.findViewById<Button>(R.id.slotSave).apply {
                isEnabled = adapter != null
                setOnClickListener { saveSlot(index) }
            }
            row.findViewById<Button>(R.id.slotClear).apply {
                isEnabled = slot != null
                setOnClickListener {
                    slotStore.clear(index)
                    Toast.makeText(this@MainActivity, getString(R.string.slot_cleared, index), Toast.LENGTH_SHORT).show()
                    renderSlots()
                }
            }
            slotContainer.addView(row)
        }
    }

    private fun saveSlot(index: Int) {
        if (buildLaunchIntent() == null) return // 選択内容のチェック（同じアプリ等）
        val l = selectedLeft() ?: return
        val r = selectedRight() ?: return
        slotStore.save(index, Slot(l.component, r.component, currentRatio(), currentMode()))
        Toast.makeText(this, getString(R.string.slot_saved, index), Toast.LENGTH_LONG).show()
        renderSlots()
    }

    private fun setUiEnabled(enabled: Boolean) {
        listOf(leftSpinner, rightSpinner, launchButton, shortcutButton, findViewById<Button>(R.id.swapButton))
            .forEach { it.isEnabled = enabled }
    }

    private companion object {
        const val RATIO_STEP = 5
        val SLOT_ICONS = intArrayOf(
            R.mipmap.ic_slot1, R.mipmap.ic_slot2, R.mipmap.ic_slot3, R.mipmap.ic_slot4, R.mipmap.ic_slot5,
        )
    }
}
