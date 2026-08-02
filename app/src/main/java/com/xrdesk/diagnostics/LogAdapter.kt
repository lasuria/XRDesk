package com.xrdesk.diagnostics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xrdesk.R
import com.xrdesk.ThemeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private val onItemClick: (DiagnosticEntry) -> Unit) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs = listOf<DiagnosticEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun submitList(newLogs: List<DiagnosticEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logs[position]
        holder.bind(entry)
        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    override fun getItemCount(): Int = logs.size

    inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        private val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        private val tvTag: TextView = view.findViewById(R.id.tvTag)
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)

        fun bind(entry: DiagnosticEntry) {
            tvLevel.text = entry.level.name
            tvTimestamp.text = timeFormat.format(Date(entry.timestamp))
            tvTag.text = entry.tag
            tvMessage.text = entry.message

            val themeColors = ThemeEngine.getColors()
            val color = when (entry.level) {
                DiagnosticEntry.Level.INFO -> themeColors.colorSuccess
                DiagnosticEntry.Level.WARNING -> 0xFFFFC107.toInt() // Amber (standard Warning)
                DiagnosticEntry.Level.ERROR -> themeColors.colorError
                DiagnosticEntry.Level.FATAL -> themeColors.colorError
            }
            tvLevel.setTextColor(color)
        }
    }
}
