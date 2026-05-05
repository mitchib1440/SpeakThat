/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.graphics.drawable.Drawable
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class HomeNotificationAdapter(
    private var notifications: List<NotificationReaderService.NotificationData>,
    private val onItemClick: (NotificationReaderService.NotificationData) -> Unit,
    private val onLongClick: (NotificationReaderService.NotificationData, View) -> Unit
) : RecyclerView.Adapter<HomeNotificationAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "HomeNotifAdapter"
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_SHOW_SYSTEM_BLOCKS_HISTORY = "show_system_blocks_history"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageAppIcon: ImageView = itemView.findViewById(R.id.imageAppIcon)
        val textAppNameTime: TextView = itemView.findViewById(R.id.textAppNameTime)
        val textNotificationTitle: TextView = itemView.findViewById(R.id.textNotificationTitle)
        val textNotificationBody: TextView = itemView.findViewById(R.id.textNotificationBody)
        val imageStatusIcon: ImageView = itemView.findViewById(R.id.imageStatusIcon)
        val textActionOutcome: TextView = itemView.findViewById(R.id.textActionOutcome)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        val context = holder.itemView.context

        val showPreciseTimestamp = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_SYSTEM_BLOCKS_HISTORY, false)
        val displayTimestamp = formatTimestamp(notification.timestamp, showPreciseTimestamp)
        holder.textAppNameTime.text = "${notification.appName} \u2022 $displayTimestamp"

        if (notification.title.isNotEmpty()) {
            holder.textNotificationTitle.text = notification.title
            holder.textNotificationTitle.visibility = View.VISIBLE
            holder.textNotificationBody.text = stripTitlePrefix(notification.title, notification.text)
        } else {
            holder.textNotificationTitle.visibility = View.GONE
            holder.textNotificationBody.text = notification.text
        }

        val icon = loadAppIcon(context, notification.packageName)
        if (icon != null) {
            holder.imageAppIcon.setImageDrawable(icon)
        } else {
            holder.imageAppIcon.setImageResource(R.drawable.ic_icon_not_found_32)
        }

        holder.itemView.setOnClickListener { onItemClick(notification) }
        holder.itemView.setOnLongClickListener {
            onLongClick(notification, it)
            true
        }

        if (notification.wasRead) {
            holder.imageAppIcon.alpha = 1.0f
            holder.textNotificationTitle.alpha = 1.0f
            holder.textNotificationBody.alpha = 1.0f
            holder.imageStatusIcon.setImageResource(R.drawable.ic_read_24)

            if (notification.spokenText != null) {
                // Grab the context from the itemView
                val context = holder.itemView.context
                // Pass the spoken text into the placeholder
                holder.textActionOutcome.text = context.getString(R.string.mainscreen_notification_history_spoke, notification.spokenText)
                holder.textActionOutcome.visibility = View.VISIBLE
            } else {
                holder.textActionOutcome.visibility = View.GONE
            }
        } else {
            holder.imageAppIcon.alpha = 0.38f
            holder.textNotificationTitle.alpha = 0.38f
            holder.textNotificationBody.alpha = 0.38f
            holder.imageStatusIcon.setImageResource(R.drawable.ic_not_read_24)

            if (notification.blockedReason != null) {
                holder.textActionOutcome.text = notification.blockedReason
                holder.textActionOutcome.visibility = View.VISIBLE
            } else {
                holder.textActionOutcome.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = notifications.size

    fun updateNotifications(newNotifications: List<NotificationReaderService.NotificationData>) {
        val oldNotifications = notifications
        val newSnapshot = newNotifications.toList()

        // Fast path for history feed behavior: new items arrive at top and
        // oldest item may fall off the bottom when capped.
        val canAnimateTopInsert = oldNotifications.isNotEmpty() &&
            newSnapshot.isNotEmpty() &&
            !areSameItem(oldNotifications.first(), newSnapshot.first()) &&
            (newSnapshot.size == oldNotifications.size || newSnapshot.size == oldNotifications.size + 1)

        if (canAnimateTopInsert) {
            val oldSize = oldNotifications.size
            val didDropBottom = newSnapshot.size == oldSize &&
                !areSameItem(oldNotifications.last(), newSnapshot.last())
            notifications = newSnapshot
            notifyItemInserted(0)
            if (didDropBottom) {
                notifyItemRemoved(oldSize)
            }
            return
        }

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldNotifications.size

            override fun getNewListSize(): Int = newSnapshot.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldNotifications[oldItemPosition]
                val newItem = newSnapshot[newItemPosition]
                return areSameItem(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldNotifications[oldItemPosition] == newSnapshot[newItemPosition]
            }
        })

        notifications = newSnapshot
        diffResult.dispatchUpdatesTo(this)
    }

    private fun areSameItem(
        oldItem: NotificationReaderService.NotificationData,
        newItem: NotificationReaderService.NotificationData
    ): Boolean {
        return oldItem.historyId == newItem.historyId
    }

    private fun loadAppIcon(context: android.content.Context, packageName: String): Drawable? {
        if (packageName == AppPickerActivity.INTERNAL_CLOCK_PACKAGE) {
            return context.getDrawable(R.drawable.ic_speakthat_clock)
        }

        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load icon for $packageName: ${e.message}")
            null
        }
    }

    private fun formatTimestamp(timestamp: String, showMilliseconds: Boolean): String {
        return try {
            if (!showMilliseconds) {
                return if (timestamp.length >= 16) timestamp.substring(11, 16) else timestamp
            }

            // Stored timestamps can be either second or millisecond precision.
            when {
                timestamp.length >= 23 -> timestamp.substring(11, 23) // HH:mm:ss.SSS
                timestamp.length >= 19 -> timestamp.substring(11, 19) // HH:mm:ss (legacy entries)
                timestamp.length >= 16 -> timestamp.substring(11, 16) // HH:mm fallback
                else -> timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }

    /**
     * The stored text often starts with "Title: Body". Strip the redundant title
     * prefix so the body text doesn't repeat the title line.
     */
    private fun stripTitlePrefix(title: String, text: String): String {
        val prefix = "$title: "
        return if (text.startsWith(prefix, ignoreCase = true)) {
            text.removePrefix(prefix).trimStart()
        } else {
            text
        }
    }
}
