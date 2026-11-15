package com.learngrow.pdfreader

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryManagerActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Learn & Grow — Memory & File Manager"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listContainer)
        scroll.addView(root)
        setContentView(scroll)

        loadRecords()
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@MemoryManagerActivity).pdfMemoryDao()
            val records = withContext(Dispatchers.IO) { dao.getAllRecords() }
            listContainer.removeAllViews()

            if (records.isEmpty()) {
                listContainer.addView(TextView(this@MemoryManagerActivity).apply {
                    text = "No viewing history saved yet."
                })
                return@launch
            }

            for (item in records) {
                val sizeStr = Formatter.formatShortFileSize(this@MemoryManagerActivity, item.fileSizeBytes)
                val row = LinearLayout(this@MemoryManagerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 20, 0, 20)
                }

                val info = TextView(this@MemoryManagerActivity).apply {
                    text = "${item.fileName} ($sizeStr)\n" +
                           "Saved Progress: Page ${item.lastPage + 1} of ${item.totalPages}\n" +
                           "Location: ${item.folderPath}"
                    textSize = 14f
                }

                val btnManage = Button(this@MemoryManagerActivity).apply {
                    text = "Manage / Delete"
                    setOnClickListener { showDeleteOptions(item) }
                }

                row.addView(info)
                row.addView(btnManage)
                listContainer.addView(row)
            }
        }
    }

    private fun showDeleteOptions(item: PdfMemory) {
        val options = arrayOf(
            "Forget Viewing Memory Only (Keep PDF file)",
            "Delete PDF File & Viewing Memory (Free device storage)"
        )
        AlertDialog.Builder(this)
            .setTitle(item.fileName)
            .setItems(options) { _, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(this@MemoryManagerActivity).pdfMemoryDao()
                    if (which == 0) {
                        dao.forgetProgressOnly(item.uriString)
                    } else {
                        try {
                            DocumentsContract.deleteDocument(contentResolver, Uri.parse(item.uriString))
                        } catch (_: Exception) { }
                        dao.forgetProgressOnly(item.uriString)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MemoryManagerActivity, "Updated", Toast.LENGTH_SHORT).show()
                        loadRecords()
                    }
                }
            }
            .show()
    }
}
