package com.teacher.raselsms.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.Holiday

class HolidayAdapter(
    private val holidays: MutableList<Holiday>,
    private val onDeleteClick: (item: Holiday) -> Unit
) : RecyclerView.Adapter<HolidayAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHolidayTitle: TextView = view.findViewById(R.id.tvHolidayTitle)
        val tvHolidayDate: TextView = view.findViewById(R.id.tvHolidayDate)
        val btnDeleteHoliday: ImageView = view.findViewById(R.id.btnDeleteHoliday)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_holiday, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = holidays[position]
        holder.tvHolidayTitle.text = item.title
        holder.tvHolidayDate.text = item.date
        holder.btnDeleteHoliday.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = holidays.size

    fun updateList(newList: List<Holiday>) {
        holidays.clear()
        holidays.addAll(newList)
        notifyDataSetChanged()
    }
}
