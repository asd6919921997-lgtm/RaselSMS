package com.teacher.raselsms.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.ClassStats
import java.util.Locale

class ClassStatsAdapter(
    private val classes: MutableList<ClassStats>,
    private val onClassClick: (item: ClassStats) -> Unit
) : RecyclerView.Adapter<ClassStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView = view.findViewById(R.id.tvClassName)
        val tvTotalStudents: TextView = view.findViewById(R.id.tvTotalStudents)
        val tvPresentCount: TextView = view.findViewById(R.id.tvPresentCount)
        val tvAbsentCount: TextView = view.findViewById(R.id.tvAbsentCount)
        val tvTodayRate: TextView = view.findViewById(R.id.tvTodayRate)
        val tvCumulativeRate: TextView = view.findViewById(R.id.tvCumulativeRate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_class_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = classes[position]
        holder.tvClassName.text = item.schoolClass.displayName
        holder.tvTotalStudents.text = "إجمالي الطلاب: ${item.totalStudents}"
        holder.tvPresentCount.text = "حاضر: ${item.presentToday}"
        holder.tvAbsentCount.text = "غائب: ${item.absentToday}"

        val todayFormatted = String.format(Locale.ENGLISH, "%.1f%%", item.todayAttendanceRate)
        val cumFormatted = String.format(Locale.ENGLISH, "%.1f%%", item.cumulativeAttendanceRate)

        holder.tvTodayRate.text = "نسبة اليوم: $todayFormatted"
        holder.tvCumulativeRate.text = "النسبة التراكمية: $cumFormatted"

        holder.itemView.setOnClickListener {
            onClassClick(item)
        }
    }

    override fun getItemCount(): Int = classes.size

    fun updateList(newList: List<ClassStats>) {
        classes.clear()
        classes.addAll(newList)
        notifyDataSetChanged()
    }
}
