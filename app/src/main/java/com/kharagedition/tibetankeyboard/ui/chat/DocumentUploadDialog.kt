package com.kharagedition.tibetankeyboard.ui.chat

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton
import com.kharagedition.tibetankeyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DocumentUpload(
    val documentId: String,
    val fileName: String,
    val fileSize: Long,
    val uploadedAt: Long,
    val language: String,
    val wordCount: Int,
    val summary: String = ""
)

/**
 * Dialog for uploading and analyzing documents
 */
class DocumentUploadDialog(
    private val onDocumentUploaded: (DocumentUpload) -> Unit
) : DialogFragment() {

    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnUpload: MaterialButton
    private lateinit var fileNameText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var uploadAnimation: LottieAnimationView
    private var selectedFileUri: Uri? = null
    private val uploadScope = CoroutineScope(Dispatchers.Main)

    private val filePickerRequest = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            updateFileDisplay(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_document_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelectFile = view.findViewById(R.id.btn_select_file)
        btnUpload = view.findViewById(R.id.btn_upload_document)
        fileNameText = view.findViewById(R.id.selected_file_name)
        progressBar = view.findViewById(R.id.upload_progress)
        uploadAnimation = view.findViewById(R.id.upload_animation)

        btnSelectFile.setOnClickListener {
            filePickerRequest.launch(arrayOf("application/pdf", "text/plain", "application/msword"))
        }

        btnUpload.setOnClickListener {
            selectedFileUri?.let { uploadDocument(it) }
        }
    }

    private fun updateFileDisplay(uri: Uri) {
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.moveToFirst()

            val fileName = it.getString(nameIndex)
            val fileSize = it.getLong(sizeIndex)

            fileNameText.text = "$fileName (${formatFileSize(fileSize)})"
            fileNameText.visibility = View.VISIBLE
        }
    }

    private fun uploadDocument(uri: Uri) {
        uploadAnimation.visibility = View.VISIBLE
        btnUpload.isEnabled = false
        progressBar.visibility = View.VISIBLE

        uploadScope.launch {
            try {
                // TODO: Upload file to Firebase Storage
                val fileName = getFileName(uri)

                // Simulate upload delay
                delay(2000)

                val document = DocumentUpload(
                    documentId = "doc_${System.currentTimeMillis()}",
                    fileName = fileName,
                    fileSize = getFileSize(uri),
                    uploadedAt = System.currentTimeMillis(),
                    language = "tibetan", // Document language identifier
                    wordCount = 0, // TODO: Calculate from content
                    summary = "" // TODO: Generate with AI
                )

                onDocumentUploaded(document)
                dismiss()

            } catch (e: Exception) {
                // Handle error
                btnUpload.isEnabled = true
                uploadAnimation.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(index)
        } ?: "document"
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.SIZE)
            it.moveToFirst()
            it.getLong(index)
        } ?: 0L
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    companion object {
        fun newInstance(onUploaded: (DocumentUpload) -> Unit): DocumentUploadDialog {
            return DocumentUploadDialog(onUploaded)
        }
    }
}
