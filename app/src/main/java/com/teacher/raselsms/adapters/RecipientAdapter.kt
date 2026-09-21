package com.teacher.raselsms.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.Recipient
import com.teacher.raselsms.models.SendStatus

class RecipientAdapter(
    private var items: List<Recipient>,
    private val onSelectionChanged: (selectedCount: Int, totalCount: Int) -> Unit
) : RecyclerView.Adapter<RecipientAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvPhoneNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipient, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvStudentName.text = item.name
        holder.tvPhoneNumber.text = item.cleanPhone

        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = item.isSelected
        holder.cbSelect.isEnabled = item.isValid

        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            item.isSelected = isChecked
            notifySelectionStats()
        }

        holder.itemView.setOnClickListener {
            if (item.isValid) {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            }
        }

        // تنسيق شارة الحالة
        when (item.status) {
            SendStatus.PENDING -> {
                holder.tvStatus.text = "في الانتظار"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
            }
            SendStatus.SENDING -> {
                holder.tvStatus.text = "جاري الإرسال..."
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DBEAFE"))
            }
            SendStatus.SENT -> {
                holder.tvStatus.text = "تم الإرسال ✓"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
            }
            SendStatus.FAILED -> {
                holder.tvStatus.text = "فشل ✕"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.danger))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
            }
            SendStatus.INVALID -> {
                holder.tvStatus.text = "رقم غير صالح ⚠"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Recipient>) {
        this.items = newItems
        notifyDataSetChanged()
        notifySelectionStats()
    }

    fun selectAll(select: Boolean) {
        for (item in items) {
            if (item.isValid) {
                item.isSelected = select
            }
        }
        notifyDataSetChanged()
        notifySelectionStats()
    }

    fun getSelectedCount(): Int = items.count { it.isSelected && it.isValid }

    private fun notifySelectionStats() {
        val validCount = items.count { it.isValid }
        val selectedCount = items.count { it.isSelected && it.isValid }
        onSelectionChanged(selectedCount, validCount)
    }
}
