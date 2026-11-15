package com.learngrow.pdfreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: ZoomableImageView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var titleText: TextView

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentBitmap: Bitmap? = null
    private var currentUri: Uri? = null
    private var currentPageIndex = 0
    private var isFullScreen = false

    private val openPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            openPdf(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            openPdf(intent.data!!)
        }
    }

    private fun setupUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        pdfView = ZoomableImageView(this).apply {
            onSingleTap = { toggleFullScreen() }
        }
        root.addView(pdfView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1E1E1E.toInt())
            setPadding(24, 32, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnOpen = Button(this).apply {
            text = "Open PDF"
            setOnClickListener { openPdfLauncher.launch(arrayOf("application/pdf")) }
        }
        titleText = TextView(this).apply {
            text = "Learn & Grow"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(24, 0, 24, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnMemory = Button(this).apply {
            text = "Memory"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, MemoryManagerActivity::class.java))
            }
        }
        topBar.addView(btnOpen)
        topBar.addView(titleText)
        topBar.addView(btnMemory)
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1E1E1E.toInt())
            setPadding(24, 16, 24, 24)
            gravity = Gravity.CENTER
        }

        val btnPrev = Button(this).apply {
            text = "◀ Prev"
            setOnClickListener { showPage(currentPageIndex - 1) }
        }
        pageIndicator = TextView(this).apply {
            text = "No PDF loaded"
            setTextColor(Color.WHITE)
            setPadding(32, 0, 32, 0)
        }
        val btnNext = Button(this).apply {
            text = "Next ▶"
            setOnClickListener { showPage(currentPageIndex + 1) }
        }
        bottomBar.addView(btnPrev)
        bottomBar.addView(pageIndicator)
        bottomBar.addView(btnNext)
        root.addView(bottomBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        setContentView(root)
    }

    private fun openPdf(uri: Uri) {
        lifecycleScope.launch {
            closeRenderer()
            currentUri = uri
            val db = AppDatabase.getDatabase(this@MainActivity).pdfMemoryDao()
            val savedRecord = withContext(Dispatchers.IO) { db.getRecord(uri.toString()) }

            try {
                fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                fileDescriptor?.let {
                    pdfRenderer = PdfRenderer(it)
                    val total = pdfRenderer!!.pageCount
                    titleText.text = getFileName(uri)

                    val startPage = savedRecord?.lastPage?.coerceIn(0, total - 1) ?: 0
                    showPage(startPage)
                    savedRecord?.let { rec -> pdfView.setZoom(rec.lastZoom) }

                    if (savedRecord != null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Resumed at Page ${startPage + 1}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Unable to open PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return

        currentPageIndex = index
        val page = renderer.openPage(index)
        val width = resources.displayMetrics.widthPixels * 2
        val height = (width.toFloat() / page.width * page.height).toInt()

        currentBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        currentBitmap = bitmap
        pdfView.resetZoom()
        pdfView.setImageBitmap(bitmap)
        pageIndicator.text = "Page ${index + 1} / ${renderer.pageCount}"
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            topBar.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        val uri = currentUri ?: return
        val renderer = pdfRenderer ?: return

        val record = PdfMemory(
            uriString = uri.toString(),
            fileName = getFileName(uri),
            folderPath = uri.path ?: "Device Storage",
            fileSizeBytes = getFileSize(uri),
            lastPage = currentPageIndex,
            totalPages = renderer.pageCount,
            lastZoom = pdfView.currentScale
        )
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@MainActivity).pdfMemoryDao().saveProgress(record)
        }
    }

    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) return cursor.getString(nameIdx)
        }
        return uri.lastPathSegment ?: "Document.pdf"
    }

    private fun getFileSize(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIdx >= 0) return cursor.getLong(sizeIdx)
        }
        return 0L
    }

    private fun closeRenderer() {
        currentBitmap?.recycle()
        currentBitmap = null
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor?.close()
        fileDescriptor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRenderer()
    }
}
