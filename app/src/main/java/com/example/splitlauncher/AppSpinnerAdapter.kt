package com.example.splitlauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

/** アイコン + アプリ名を表示するプルダウン用アダプター */
class AppSpinnerAdapter(
    context: Context,
    private val apps: List<AppInfo>,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = apps.size
    override fun getItem(position: Int) = apps[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    private fun bind(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false)
        val app = apps[position]
        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.icon)
        view.findViewById<TextView>(R.id.appLabel).text = app.label
        return view
    }

    fun indexOf(flattenedComponent: String?): Int =
        apps.indexOfFirst { it.component.flattenToString() == flattenedComponent }
}
