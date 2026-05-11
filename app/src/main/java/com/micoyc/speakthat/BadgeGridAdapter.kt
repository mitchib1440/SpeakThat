package com.micoyc.speakthat

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

        if (isUnlocked) {
            holder.imageBadge.colorFilter = null
            holder.imageLockOverlay.visibility = View.GONE
        } else {
            holder.imageBadge.colorFilter = grayscaleFilter
            holder.imageLockOverlay.visibility = View.VISIBLE
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
    }
}
