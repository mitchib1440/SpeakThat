package com.micoyc.speakthat

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeNotificationAdapter(
    private var notifications: List<NotificationReaderService.NotificationData>,
    private val onItemClick: (NotificationReaderService.NotificationData) -> Unit,
    private val onLongClick: (NotificationReaderService.NotificationData, View) -> Unit
) : RecyclerView.Adapter<HomeNotificationAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "HomeNotifAdapter"
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

        val displayTimestamp = formatTimestamp(notification.timestamp)
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
            holder.imageAppIcon.setImageResource(R.drawable.speakthaticon)
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
        notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun loadAppIcon(context: android.content.Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load icon for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Formats "yyyy-MM-dd HH:mm:ss" into "HH:mm" for compact display.
     */
    private fun formatTimestamp(timestamp: String): String {
        return try {
            if (timestamp.length >= 16) timestamp.substring(11, 16) else timestamp
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
