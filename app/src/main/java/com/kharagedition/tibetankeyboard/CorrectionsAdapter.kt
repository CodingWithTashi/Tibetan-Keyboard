package com.kharagedition.tibetankeyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class CorrectionItem(
    val incorrectWord: String,
    val correctWord: String,
    val reason: String,
    val wordIndex: Int
)
class CorrectionsAdapter(private val corrections: List<CorrectionItem>) :
    RecyclerView.Adapter<CorrectionsAdapter.CorrectionViewHolder>() {

    class CorrectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val incorrectText: TextView = itemView.findViewById(R.id.incorrect_text)
        val correctText: TextView = itemView.findViewById(R.id.correct_text)
        val reasonText: TextView = itemView.findViewById(R.id.reason_text)
        val arrowIcon: View = itemView.findViewById(R.id.arrow_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CorrectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_correction, parent, false)
        return CorrectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CorrectionViewHolder, position: Int) {
        val correction = corrections[position]

        holder.incorrectText.text = correction.incorrectWord
        holder.correctText.text = correction.correctWord
        holder.reasonText.text = correction.reason

        // Add slight animation
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(position * 50L)
            .start()
    }

    override fun getItemCount() = corrections.size
}
