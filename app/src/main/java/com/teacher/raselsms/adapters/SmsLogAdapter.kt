package com.teacher.raselsms.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.SmsLogEntry

class SmsLogAdapter(
    private val logs: MutableList<SmsLogEntry>
) : RecyclerView.Adapter<SmsLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLogName: TextView = view.findViewById(R.id.tvLogName)
        val tvLogPhone: TextView = view.findViewById(R.id.tvLogPhone)
        val tvLogTime: TextView = view.findViewById(R.id.tvLogTime)
        val tvLogSim: TextView = view.findViewById(R.id.tvLogSim)
        val tvLogStatus: TextView = view.findViewById(R.id.tvLogStatus)
        val tvLogMessage: TextView = view.findViewById(R.id.tvLogMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = logs[position]
        holder.tvLogName.text = item.studentName.ifBlank { "مستلم غير مسمى" }
        holder.tvLogPhone.text = item.phone
        holder.tvLogTime.text = item.sentAt
        holder.tvLogSim.text = "شريحة ${item.simSlot + 1}"
        holder.tvLogMessage.text = item.message

        if (item.status == "SENT") {
            holder.tvLogStatus.text = "تم الإرسال ✓"
            holder.tvLogStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            holder.tvLogStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            holder.tvLogStatus.text = "فشل ✕"
            holder.tvLogStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            holder.tvLogStatus.setTextColor(Color.parseColor("#C62828"))
        }
    }

    override fun getItemCount(): Int = logs.size

    fun updateList(newList: List<SmsLogEntry>) {
        logs.clear()
        logs.addAll(newList)
        notifyDataSetChanged()
    }
}
