package com.teacher.raselsms.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.StudentStats
import java.util.Locale

class StudentStatsAdapter(
    private val students: MutableList<StudentStats>
) : RecyclerView.Adapter<StudentStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        val tvPresentDays: TextView = view.findViewById(R.id.tvPresentDays)
        val tvAbsentDays: TextView = view.findViewById(R.id.tvAbsentDays)
        val tvAttendanceRate: TextView = view.findViewById(R.id.tvAttendanceRate)
        val progressAttendance: ProgressBar = view.findViewById(R.id.progressAttendance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = students[position]
        holder.tvStudentName.text = item.student.name
        holder.tvStudentPhone.text = item.student.phone
        holder.tvPresentDays.text = "حضور: ${item.presentDays} يوم"
        holder.tvAbsentDays.text = "غياب: ${item.absentDays} يوم"

        val rateFormatted = String.format(Locale.ENGLISH, "%.1f%%", item.attendanceRate)
        holder.tvAttendanceRate.text = rateFormatted
        holder.progressAttendance.progress = item.attendanceRate.toInt().coerceIn(0, 100)
    }

    override fun getItemCount(): Int = students.size

    fun updateList(newList: List<StudentStats>) {
        students.clear()
        students.addAll(newList)
        notifyDataSetChanged()
    }
}
