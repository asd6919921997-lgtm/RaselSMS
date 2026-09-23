package com.teacher.raselsms.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teacher.raselsms.R
import com.teacher.raselsms.models.StudentAttendanceItem

class AttendanceStudentAdapter(
    private val students: MutableList<StudentAttendanceItem>,
    private val onAttendanceChanged: (item: StudentAttendanceItem, position: Int) -> Unit
) : RecyclerView.Adapter<AttendanceStudentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        val btnToggleStatus: TextView = view.findViewById(R.id.btnToggleStatus)
        val tvNumberBadge: TextView = view.findViewById(R.id.tvNumberBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = students[position]
        holder.tvNumberBadge.text = "${position + 1}"
        holder.tvStudentName.text = item.student.name
        holder.tvStudentPhone.text = item.student.phone

        updateStatusView(holder.btnToggleStatus, item.isAbsent)

        holder.itemView.setOnClickListener {
            item.isAbsent = !item.isAbsent
            updateStatusView(holder.btnToggleStatus, item.isAbsent)
            onAttendanceChanged(item, position)
        }

        holder.btnToggleStatus.setOnClickListener {
            item.isAbsent = !item.isAbsent
            updateStatusView(holder.btnToggleStatus, item.isAbsent)
            onAttendanceChanged(item, position)
        }
    }

    private fun updateStatusView(tv: TextView, isAbsent: Boolean) {
        if (isAbsent) {
            tv.text = "غائب ✕"
            tv.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100")) // برتقالي داكن / أحمر
            tv.setTextColor(Color.WHITE)
        } else {
            tv.text = "حاضر ✓"
            tv.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32")) // أخضر زاهي
            tv.setTextColor(Color.WHITE)
        }
    }

    override fun getItemCount(): Int = students.size

    fun updateList(newList: List<StudentAttendanceItem>) {
        students.clear()
        students.addAll(newList)
        notifyDataSetChanged()
    }

    fun markAll(absent: Boolean) {
        for (item in students) {
            item.isAbsent = absent
        }
        notifyDataSetChanged()
    }
}
