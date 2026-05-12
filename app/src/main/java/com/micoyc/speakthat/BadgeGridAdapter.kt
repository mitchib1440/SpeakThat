package com.micoyc.speakthat

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BadgeGridAdapter(
    private val allTiers: List<BadgeAssets.BadgeTier>,
    private val unlockedTiers: List<BadgeAssets.BadgeTier>,
    var selectedKey: String,
    private val festiveEnabled: Boolean,
    private val onItemClick: (BadgeAssets.BadgeTier, Boolean) -> Unit
) : RecyclerView.Adapter<BadgeGridAdapter.BadgeViewHolder>() {

    private val grayscaleFilter: ColorMatrixColorFilter

    init {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        grayscaleFilter = ColorMatrixColorFilter(matrix)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge_grid, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val tier = allTiers[position]
        val isUnlocked = unlockedTiers.contains(tier)
        val isSelected = tier.key == selectedKey

        val drawableRes = if (festiveEnabled) {
            tier.festiveDrawableRes ?: tier.drawableRes
        } else {
            tier.drawableRes
        }
        
        holder.imageBadge.setImageResource(drawableRes)

        // Set badge name
        val context = holder.itemView.context
        val stringResId = context.resources.getIdentifier("badge_option_${tier.key}", "string", context.packageName)
        if (stringResId != 0) {
            holder.textBadgeName.setText(stringResId)
        } else {
            holder.textBadgeName.text = tier.key // Fallback
        }

        if (isUnlocked) {
            holder.imageBadge.colorFilter = null
            holder.imageLockOverlay.visibility = View.GONE
            holder.textBadgeName.alpha = 1.0f
        } else {
            holder.imageBadge.colorFilter = grayscaleFilter
            holder.imageLockOverlay.visibility = View.VISIBLE
            holder.textBadgeName.alpha = 0.5f
        }

        holder.viewSelectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onItemClick(tier, isUnlocked)
        }
    }

    override fun getItemCount(): Int = allTiers.size

    class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageBadge: ImageView = itemView.findViewById(R.id.imageBadge)
        val imageLockOverlay: ImageView = itemView.findViewById(R.id.imageLockOverlay)
        val viewSelectionBorder: View = itemView.findViewById(R.id.viewSelectionBorder)
        val textBadgeName: TextView = itemView.findViewById(R.id.textBadgeName)
    }
}
