package ch.derlin.changelog

import android.content.Context
import android.content.res.Resources
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat


/**
 * adapter.tk
 * Defines classes and adapters for the changelog dialog view.
 *
 * Copyright (C) 2018 Lucy Linder (derlin)
 *
 * This software may be modified and distributed under the terms
 * of the Apache 2.0 license.  See the LICENSE file for details.
 */
/**
 *  Updated to AndroidX: Copyright (C) 2019 Arne Rantzen (Tyxz)
 */

/** Holds information about one "item", i.e. a <change> */
open class ChangelogItem(val text: String, val type: String)

/** Holds information about one "header", i.e. a <release> */
class ChangelogHeader(version: String, val date: String? = null, val summary: String? = null, type : String = "default") : ChangelogItem(version, type)


/** Base Holder for the [ChangelogAdapter]. Used for [ChangelogItem]. */
open class Holder(val v: View) : RecyclerView.ViewHolder(v) {
    val textview = v.findViewById<TextView>(R.id.text)
    var bullet : String = "default"
}

/** Holder for the [ChangelogAdapter]. Used for [ChangelogHeader]. */
class HeaderHolder(v: View) : Holder(v) {
    val dateView = v.findViewById<TextView>(R.id.date)
    val summaryView = v.findViewById<TextView>(R.id.summary)

    var summary: String? = null
        set(value) {
            summaryView.text = value
            summaryView.visibility = if (value != null) View.VISIBLE else View.GONE
            field = value
        }
}

/** An adapter for [ChangelogItem] and [ChangelogHeader]. Nothing special, except that
 * it will create two types of holders, depending on the data type. */
class ChangelogAdapter(val list: List<ChangelogItem>) :
        RecyclerView.Adapter<Holder>() {

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int =
            if (list[position] is ChangelogHeader) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        var name = ""
        try {
            return if (viewType > 0) {
                name = parent.context.resources.getResourceEntryName(R.layout.changelog_cell_header)
                HeaderHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.changelog_cell_header, parent, false))
            } else {
                name = parent.context.resources.getResourceEntryName(R.layout.changelog_cell)
                Holder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.changelog_cell, parent, false))
            }
        } catch (tr: Throwable) {
            throw Exception("Error creating viewHolder. viewType[$viewType] view[${name}]",tr)
        }
    }

    @Nullable
    fun getAttributeDrawable(
            context: Context,
            attributeId: Int): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attributeId, typedValue, true)
        val drawableRes = typedValue.resourceId
        var drawable: Drawable? = null
        try {
            drawable = ContextCompat.getDrawable(context, drawableRes)
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
        }

        return drawable
    }




    override fun onBindViewHolder(h: Holder, position: Int) {
        val item = list[position]
        h.apply {
            textview.text = item.text
            if (this is HeaderHolder) {
                val header = item as ChangelogHeader
                summary = header.summary
                dateView.text = header.date
            } else {
                bullet = item.type
                val icon = when (bullet) {
                    "add" -> R.attr.changelogBulletIconAdd
                    "bug" -> R.attr.changelogBulletIconBug
                    "notice" -> R.attr.changelogBulletIconNotice
                    "remove" -> R.attr.changelogBulletIconRemove
                    else -> R.attr.changelogBulletIconDefault
                }
                h.v.findViewById<View>(R.id.bullet)?.background = getAttributeDrawable(h.v.context, icon)
            }
        }
    }
}