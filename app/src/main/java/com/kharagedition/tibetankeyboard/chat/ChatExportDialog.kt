package com.kharagedition.tibetankeyboard.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.kharagedition.tibetankeyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dialog for exporting chat conversations
 */
class ChatExportDialog(
    private val conversationId: String,
    private val onExportStart: (String) -> Unit
) : DialogFragment() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var btnExport: MaterialButton
    private val exportScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_chat_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroup = view.findViewById(R.id.export_format_group)
        btnExport = view.findViewById(R.id.btn_export)

        // Set default selection
        view.findViewById<RadioButton>(R.id.format_pdf).isChecked = true

        btnExport.setOnClickListener {
            val selectedFormat = when (radioGroup.checkedRadioButtonId) {
                R.id.format_pdf -> "pdf"
                R.id.format_txt -> "txt"
                R.id.format_markdown -> "markdown"
                else -> "pdf"
            }

            exportConversation(selectedFormat)
        }
    }

    private fun exportConversation(format: String) {
        btnExport.isEnabled = false
        btnExport.text = "Exporting..."

        exportScope.launch {
            try {
                // TODO: Implement export logic
                // 1. Fetch all messages from Firestore
                // 2. Format based on selected format
                // 3. Save to file or upload to Cloud Storage
                // 4. Share with user

                onExportStart(format)

                // Simulate delay
                kotlinx.coroutines.delay(1500)

                dismiss()

            } catch (e: Exception) {
                btnExport.isEnabled = true
                btnExport.text = "Export"
            }
        }
    }

    companion object {
        fun newInstance(
            conversationId: String,
            onExportStart: (String) -> Unit
        ): ChatExportDialog {
            return ChatExportDialog(conversationId, onExportStart)
        }
    }
}
