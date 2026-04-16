package com.cityhub.agent

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Representa um app instalado. [isSelected] é mutável pelo CheckBox. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    var isSelected: Boolean = false
)

/**
 * Adapter reutilizado tanto na grade do launcher quanto na tela de gerenciamento.
 *
 * @param showCheckbox Quando verdadeiro, exibe o CheckBox para seleção de allow/deny.
 * @param onClick      Callback ao tocar no item (não usado quando showCheckbox = true).
 */
class AppAdapter(
    private val apps: List<AppInfo>,
    private val showCheckbox: Boolean = false,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon:     ImageView = view.findViewById(R.id.ivAppIcon)
        val label:    TextView  = view.findViewById(R.id.tvAppLabel)
        val checkbox: CheckBox  = view.findViewById(R.id.cbApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label

        if (showCheckbox) {
            holder.checkbox.visibility = View.VISIBLE
            // Remove listener antes de setar o estado para não disparar callback falso
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected
            holder.checkbox.setOnCheckedChangeListener { _, checked ->
                app.isSelected = checked
            }
        } else {
            holder.checkbox.visibility = View.GONE
            holder.itemView.setOnClickListener { onClick(app) }
        }
    }

    override fun getItemCount(): Int = apps.size
}
