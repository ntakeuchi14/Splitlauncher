package com.example.splitlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.text.Collator

data class AppInfo(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
)

object AppRepository {

    /** ランチャーに表示されるインストール済みアプリを名前順で返す（自分自身は除外）。重いのでバックグラウンドで呼ぶこと。 */
    fun loadLaunchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    icon = ri.loadIcon(pm),
                )
            }
            .distinctBy { it.component }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }
}
