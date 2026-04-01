package com.kharagedition.tibetankeyboard.ui.grammar

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.kharagedition.tibetankeyboard.R

/**
 * Adapter for displaying grammar corrections
 */
class GrammarCorrectionAdapter(
    private val onCorrectionClick: (GrammarCorrection) -> Unit
) : ListAdapter<GrammarCorrection, GrammarCorrectionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grammar_correction, parent, false)
        return ViewHolder(view as MaterialCardView, onCorrectionClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val cardView: MaterialCardView,
        private val onCorrectionClick: (GrammarCorrection) -> Unit
    ) : RecyclerView.ViewHolder(cardView) {

        private val originalText: TextView = cardView.findViewById(R.id.original_text)
        private val correctedText: TextView = cardView.findViewById(R.id.corrected_text)
        private val reason: TextView = cardView.findViewById(R.id.correction_reason)
        private val confidence: TextView = cardView.findViewById(R.id.confidence_score)

        fun bind(correction: GrammarCorrection) {
            originalText.text = correction.originalText
            correctedText.text = correction.correctedText
            reason.text = correction.reason

            // Set confidence color based on score
            val confidencePercent = (correction.confidence * 100).toInt()
            confidence.text = "$confidencePercent% confident"

            val confidenceColor = when {
                correction.confidence >= 0.9 -> ContextCompat.getColor(
                    cardView.context,
                    R.color.success_green
                )
                correction.confidence >= 0.7 -> ContextCompat.getColor(
                    cardView.context,
                    R.color.warning_orange
                )
                else -> ContextCompat.getColor(cardView.context, R.color.error_red)
            }
            confidence.setTextColor(confidenceColor)

            // Add smooth entry animation
            cardView.alpha = 0f
            cardView.translationY = 50f
            cardView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()

            cardView.setOnClickListener {
                onCorrectionClick(correction)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GrammarCorrection>() {
        override fun areItemsTheSame(oldItem: GrammarCorrection, newItem: GrammarCorrection): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GrammarCorrection, newItem: GrammarCorrection): Boolean =
            oldItem == newItem
    }
}
