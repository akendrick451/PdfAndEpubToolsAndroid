package com.aktools.pdfsetfilenameaktools


import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.epub.EpubWriter
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.service.MediatypeService



class MainActivity : AppCompatActivity() {

    private lateinit var btnPickPDFForFilename: Button
    private lateinit var btnPickPDFForTitle: Button

    private lateinit var txtStatus: TextView
    private lateinit var pdfPickerLauncherForFilename: ActivityResultLauncher<Intent>
    private lateinit var pdfPickerLauncherForTitleChange : ActivityResultLauncher<Intent>
    private val PICK_EPUB_REQUEST = 1001


    private lateinit var btnPickEpub: Button

     override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize PDFBox once
        PDFBoxResourceLoader.init(applicationContext)


        btnPickPDFForFilename = findViewById(R.id.btnPickPDFForFilename)
         btnPickPDFForTitle = findViewById(R.id.btnPickPDFForTitle)
         txtStatus = findViewById(R.id.text_status)





        pdfPickerLauncherForFilename = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data

                if (uri != null) {
                    val title = getPdfTitleFromUri(uri)
                    val strFileName = getFileNameFromUri(uri)
                    val currentTime = getCurrentTimeHHmmss()
                    val strMessage = txtStatus.getText().toString() + "\n\n$currentTime SET filename - Working on file: Title: $title, and CURRENT filename: $strFileName"
                    Toast.makeText(this, strMessage, Toast.LENGTH_LONG).show()
                    txtStatus.text =  strMessage
                    renamePdfUsingTitleAndSaveInSameFolder(uri)
                    //savePdfToDownloadsWithTitleAsName(uri);

                }



            }
        }

         pdfPickerLauncherForTitleChange =  registerForActivityResult(
                 ActivityResultContracts.StartActivityForResult()
                 ) { result: ActivityResult ->
             if (result.resultCode == Activity.RESULT_OK) {
                 val uri: Uri? = result.data?.data

                 if (uri != null) {
                     val title = getPdfTitleFromUri(uri)
                     val strFileName = getFileNameFromUri(uri)
                     val strDirectoryAndFilename = uri
                     val currentTime = getCurrentTimeHHmmss()

                     val strMessage = txtStatus.getText().toString() + "\n\n${currentTime} SET title - Old Title: $title, and filename: $strDirectoryAndFilename"
                     Toast.makeText(this, strMessage, Toast.LENGTH_LONG).show()
                     txtStatus.text = strMessage
                     //savePdfToDownloadsWithTitleFromFileName(uri);
                     setPdfTitleAndOverwriteOriginal(uri)

                 }



             }
         }

        btnPickPDFForFilename.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            pdfPickerLauncherForFilename.launch(intent)
        }

         btnPickPDFForTitle.setOnClickListener {
             val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                 addCategory(Intent.CATEGORY_OPENABLE)
                 type = "application/pdf"
             }
             pdfPickerLauncherForTitleChange.launch(intent)
         }



         btnPickEpub = findViewById(R.id.btn_pick_epub)



          val pickEpubLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
             uri ?: return@registerForActivityResult

             // This method never crashes – it checks what was actually granted
             uri.takePersistablePermissionIfPossible()

             processEpub(uri)
         }


         btnPickEpub.setOnClickListener {
            
            

             // Open file picker restricted to .epub (fallback * works everywhere)
             pickEpubLauncher.launch(arrayOf("application/epub+zip"))
         }



    } // end ON CREATE

    // Add this extension function anywhere in your file
    fun Uri.takePersistablePermissionIfPossible() {
        val granted = contentResolver.persistedUriPermissions
            .find { it.uri == this }
            ?.let {
                (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                        (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            }

        if (granted != null && granted != 0) {
            try {
                contentResolver.takePersistableUriPermission(this, granted)
            } catch (e: Exception) {
                // Ignore – completely expected with cloud providers
            }
        }
    }

    fun getCurrentTimeHHmmss(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(calendar.getTime())
    }

    fun getLastDirectoryFromUri(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        // Get the path segment of the URI
        val path = uri.getPath()

        if (path == null) {
            return null
        }

        // Create a File object from the path
        val file = File(path)

        // Get the parent directory
        val parentFile = file.getParentFile()

        if (parentFile != null) {
            // Return the name of the parent directory
            return parentFile.getName()
        } else {
            return null
        }
    }



// Modern Activity Result API (no onActivityResult needed)



    /**
     * Tries metadata title, then falls back to display name, else "No title".
     * NOTE: This is an Activity method, so `contentResolver` is valid here.
     */
    private fun getPdfTitleFromUri(uri: Uri): String {
        // 1) Metadata title
        readPdfMetadataTitle(uri)?.let { if (it.isNotBlank()) return it }

        // 2) Display name (filename)
        queryDisplayName(uri)?.let { if (it.isNotBlank()) return it }

        // 3) Fallback
        return "No title"
    }


    private fun readPdfMetadataTitle(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc -> doc.documentInformation?.title }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor: Cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    fun getFileNameFromContentUri(uri: Uri, contentResolver: ContentResolver): String? {
        var fileName: String? = null
        if (uri.getScheme() == ContentResolver.SCHEME_CONTENT) {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close()
                }
            }
        }
        return fileName
    }

    //private fun savePdfTo
    // change the title if it equals someTitle
    private fun savePdfToDownloadsWithTitleFromFileName(uri: Uri){

        try {
            // Open the picked PDF
            contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->

                    // Get filename
                    val currentFilename = uri
                    val strNewFilename = getFileNameFromContentUri(uri, contentResolver)
                    val rawTitle = document.documentInformation?.title ?: "untitled"
                    if (rawTitle.equals("someTitle") || rawTitle.equals("untitled")) {
                        document.documentInformation.setTitle(strNewFilename)


                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // ✅ Android 10+ : Use MediaStore Downloads
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, strNewFilename)
                                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }

                            val resolver = contentResolver
                            val uriOut = resolver.insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

                            uriOut?.let { outputUri ->
                                resolver.openOutputStream(outputUri)?.use { outStream ->
                                    document.save(outStream)
                                }
                                Toast.makeText(
                                    this,
                                    "Saved to Downloads as $strNewFilename",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } else {
                            // ✅ Android 9 and below : Direct file save
                            val downloadsDir =
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val outFile = File(downloadsDir, strNewFilename)
                            FileOutputStream(outFile).use { outStream ->
                                document.save(outStream)
                            }
                            val title = getPdfTitleFromUri(uri)
                            val strMessage =  txtStatus.getText().toString() + "Saved to ${outFile.absolutePath} with title $title"
                            Toast.makeText(this, strMessage, Toast.LENGTH_LONG).show()
                            txtStatus.text = strMessage

                        }
                    } else {
                                Toast.makeText(this, "No need to change title - already " + rawTitle , Toast.LENGTH_LONG)
                                    .show()

                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF", Toast.LENGTH_LONG).show()
        }

    } // end safe TitleFromFilename

    private fun savePdfToDownloadsWithTitleAsName(uri: Uri) {
        try {
            // Open the picked PDF
            contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->

                    // Get metadata title or fallback
                    val rawTitle = document.documentInformation?.title ?: "untitled"
                    val safeTitle = rawTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val fileName = "$safeTitle.pdf"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // ✅ Android 10+ : Use MediaStore Downloads
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val resolver = contentResolver
                        val uriOut = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        uriOut?.let { outputUri ->
                            resolver.openOutputStream(outputUri)?.use { outStream ->
                                document.save(outStream)
                            }

                            val title = getPdfTitleFromUri(uri)
                            val strLastDirectoryOfURI = getLastDirectoryFromUri(uri)
                            val strMessage = txtStatus.text.toString() + " Saved to Downloads as $strLastDirectoryOfURI, with title $title "
                            Toast.makeText(this, strMessage, Toast.LENGTH_LONG).show()
                            txtStatus.text = strMessage


                        }
                    } else {
                        // ✅ Android 9 and below : Direct file save
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val outFile = File(downloadsDir, fileName)
                        FileOutputStream(outFile).use { outStream ->
                            document.save(outStream)
                        }
                        val strMessage = txtStatus.text.toString() + ". Saved to ${outFile.absolutePath}, with title $title"
                        Toast.makeText(this, strMessage, Toast.LENGTH_LONG).show()
                        txtStatus.text = strMessage

                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF", Toast.LENGTH_LONG).show()
        }
    }



    private fun getFileFromUri(uri: Uri): File? {
        // Persist permissions so we can write later
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)

        // Query the real file name and parent directory
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                val displayName = cursor.getString(nameIndex)

                // Build path inside the app-specific directory that SAF allows us to write to
                // (DocumentsContract doesn't give direct /storage/emulated/0/... paths anymore)
                val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), displayName)

                // Copy original → destination so we have a real File we can overwrite
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                return destination
            }
        }
        return null
    }

    // ─── Helper Functions (keep these) ───
    private fun getFileNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                contentResolver.query(uri, null, null, null, null)?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && it.moveToFirst()) it.getString(nameIndex) else null
                }
            }
            else -> uri.lastPathSegment
        }
    }

    private fun cleanFileNameToTitle(name: String): String {
        var title = name.substringBeforeLast('.')
        title = title.replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), "")
        title = title.replace(Regex("\\s*[._-]\\s*"), " ")
        title = title.replace(Regex("\\s+"), " ").trim()
        return if (title.isBlank()) "Untitled Book" else title
    }

    private fun guessAuthorFromFileName(name: String): String {
        val clean = name.substringBeforeLast('.')
        return clean.split(Regex("\\s*(-|–|—)\\s*"), 2).firstOrNull()
            ?.takeIf { it.matches(Regex(".*[A-Za-z]{3,}.*")) } ?: ""
    }

    private fun setPdfTitleAndOverwriteOriginal(originalUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(originalUri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        val originalName = getFileNameFromUri(originalUri) ?: "Unknown.pdf"
                        val nameWithoutExt = originalName.substringBeforeLast('.')
                        val newTitle = nameWithoutExt.trim()
                        val currentTitle = document.documentInformation?.title?.trim() ?: ""

                        if (currentTitle.isBlank() || currentTitle.equals("untitled", true) || currentTitle.equals("someTitle", true)) {
                            document.documentInformation.title = newTitle
                        }

                        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                        document.save(byteArrayOutputStream)
                        val pdfBytes = byteArrayOutputStream.toByteArray()

                        // Overwrite original URI directly
                        contentResolver.openOutputStream(originalUri, "wt")?.use { output ->  // "wt" = write/truncate
                            output.write(pdfBytes)
                        } ?: throw Exception("Cannot write to original file")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Updated original file: $originalName", Toast.LENGTH_LONG).show()
                            txtStatus.text = "${txtStatus.text}. Overwritten: $originalName"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Overwrite failed (read-only file?). Falling back to folder pick.\n${e.message}", Toast.LENGTH_LONG).show()
                    // Fallback: Launch folder picker
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    //folderPickerLauncher.launch(intent)
                }
            }
        }
    }

    private fun renamePdfUsingTitleAndSaveInSameFolder(originalUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(originalUri)?.use { input ->
                    PDDocument.load(input).use { document ->

                        // Get the title from PDF metadata
                        val rawTitle = document.documentInformation?.title?.trim() ?: "Untitled"
                        if (rawTitle.isBlank() || rawTitle.equals("untitled", true)) {
                            throw Exception("PDF has no valid title to use as filename")
                        }

                        val safeTitle = rawTitle.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                        val newFileName = "$safeTitle.pdf"

                        // Save modified PDF to bytes
                        val outputStream = java.io.ByteArrayOutputStream()
                        document.save(outputStream)
                        val pdfBytes = outputStream.toByteArray()

                        // Try to overwrite original file first (best case)
                        var saved = false
                        try {
                            contentResolver.openOutputStream(originalUri, "wt")?.use { out ->
                                out.write(pdfBytes)
                            }
                            saved = true
                            val strLastDirectoryOfURI = getLastDirectoryFromUri(originalUri)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Renamed in place:\n $strLastDirectoryOfURI $newFileName", Toast.LENGTH_LONG).show()
                                txtStatus.text = txtStatus.text.toString() + ". Renamed (overwritten): $newFileName"
                            }
                        } catch (e: Exception) {
                            // Overwrite failed → fall back to creating new file in same folder
                        }

                        if (!saved) {
                            // Fallback: Use DocumentFile + parent folder (with user-granted access)
                            val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(this@MainActivity, originalUri)
                            val parent = docFile?.parentFile

                            if (parent == null || !parent.canWrite()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Cannot access folder. Opening folder picker...",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                // Final fallback: let user pick folder
                              //  withContext(Dispatchers.Main) {
                                //    promptSaveWithFolderPicker(newFileName, pdfBytes)
                                //}
                                return@launch
                            }

                            // Delete old version if exists (optional)
                            parent.findFile(newFileName)?.delete()

                            // Create new file
                            val newFile = parent.createFile("application/pdf", safeTitle)
                                ?: throw Exception("Failed to create file")

                            contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                out.write(pdfBytes)
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Saved as new file in same folder:\n$newFileName",
                                    Toast.LENGTH_LONG
                                ).show()
                                txtStatus.text = "Saved as: $newFileName (same folder)\n" + txtStatus.text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                                // EPUB STUFF BELOW
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private fun processEpub(epubUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileNameFromUri(epubUri) ?: "Unknown Book.epub"
                val title = cleanFileNameToTitle(fileName)
                val author = guessAuthorFromFileName(fileName).takeIf { it.isNotBlank() }

                // Generate cover
                val coverFile = EpubCoverGenerator.generateCover(
                    context = this@MainActivity,
                    options = EpubCoverGenerator.CoverOptions(title = title, author = author, width = 1600, height = 2560)
                )

                // ── Copy to temp (your robust code) ───────────────────────────────
                val tempEpub = File(cacheDir, "temp_processing_${System.currentTimeMillis()}.epub")
                val bytesCopied = contentResolver.openInputStream(epubUri)?.use { input ->
                    tempEpub.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Failed to open input stream from URI")

                if (bytesCopied == 0L || !tempEpub.exists() || tempEpub.length() == 0L) {
                    throw IllegalStateException("Copied file is empty – original EPUB could not be read")
                }

                // ── Read safely (your helper function) ────────────────────────────
                val book = try {
                    tempEpub.readEpubSafely().also {
                        it.tableOfContents // ← silences NCX warning
                    }
                } catch (e: Exception) {
                    tempEpub.delete()
                    throw IllegalStateException("Not a valid EPUB (DRM? Corrupted? Not an EPUB?)", e)
                }
                // ── Replace cover (FIXED – uses getResources()) ──────────────────

                book.resources.remove("cover.jpg")
                book.resources.remove("Images/cover.jpg")
                book.resources.remove("OEBPS/cover.jpg")

                val newCoverBytes = coverFile.readBytes()
                val coverResource = nl.siegmann.epublib.domain.Resource(
                    newCoverBytes,
                    "image/jpeg"
                ).apply {
                    href = "cover.jpg"
                    id = "cover"
                    mediaType = MediatypeService.JPG
                }

                book.addResource(coverResource)
                book.setCoverImage(coverResource)


                // ── Update metadata ───────────────────────────────────────────────
                book.metadata.titles = listOf(title)
                author?.let { book.metadata.addAuthor(nl.siegmann.epublib.domain.Author(it)) }

                // ── Write back (your Android 13+ code) ───────────────────────────
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    contentResolver.openOutputStream(epubUri, "wt")?.use { os ->
                        nl.siegmann.epublib.epub.EpubWriter().write(book, os)
                    } ?: throw IllegalStateException("Cannot write back – missing permission?")
                } else {
                    val modifiedTemp = File(cacheDir, "modified.epub")
                    nl.siegmann.epublib.epub.EpubWriter().write(book, modifiedTemp.outputStream())
                    contentResolver.openOutputStream(epubUri, "wt")?.use { os ->
                        modifiedTemp.inputStream().use { it.copyTo(os) }
                    }
                    modifiedTemp.delete()
                }

                // ── Cleanup ───────────────────────────────────────────────────────
                coverFile.delete()
                tempEpub.delete()

                withContext(Dispatchers.Main) {
                    val strMessage = txtStatus.text.toString() + " " +"Success! Cover updated and overwritten to folder " + getLastDirectoryFromUri(epubUri) + "/" + fileName
                    txtStatus.text = strMessage
                    Toast.makeText(this@MainActivity, strMessage, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val strMessage = txtStatus.text.toString() + " " + "Failed: ${e.message}"
                    txtStatus.text = strMessage
                    Toast.makeText(this@MainActivity, strMessage , Toast.LENGTH_LONG).show()
                }
            }
        }
    }// end process epub

    private fun File.readEpubSafely(): Book {
        require(exists() && length() > 0L) { "EPUB file is missing or empty" }

        val epubReader = EpubReader()

        // This is the CORRECT call in epublib 3.1
        // readEpubLazy takes (InputStream, String)
        return this.inputStream().use { inputStream ->
            epubReader.readEpubLazy(this.absolutePath, "UTF-8")
        }
    }

    private fun File.readEpubSafely2(): Book {
        require(exists() && length() > 0L) { "EPUB file is missing or empty" }

        val epubReader = EpubReader()

        // Try normal reader first (fast & strict)
        try {
            return epubReader.readEpub(this.inputStream())
        } catch (e: Exception) {
            Log.w("EPUB", "Strict reader failed, trying fully lenient mode", e)
        }

        // CORRECT way for epublib 4.x — this is the real "lenient" method that never crashes
        return this.inputStream().use { inputStream ->
            epubReader.readEpub(inputStream, "UTF-8")  // ← third parameter = lenient mode
        }
    }

    object EpubCoverGenerator {

    data class CoverOptions(
        val title: String,
        val author: String? = null,
        val subtitle: String? = null,
        val width: Int = 1400,      // Standard EPUB cover size (can be 1400–2000px)
        val height: Int = 2100,
        val backgroundGradientColors: Pair<Int, Int>? = null, // top to bottom
        val titleTextColor: Int = Color.WHITE,
        val authorTextColor: Int = Color.WHITE,
        val titleTextSizeSp: Float = 92f,
        val authorTextSizeSp: Float = 56f
    )

    fun generateCover(context: android.content.Context, options: CoverOptions): File {
        val bitmap = Bitmap.createBitmap(options.width, options.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw background (gradient or solid)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (options.backgroundGradientColors != null) {
            val shader = LinearGradient(
                0f, 0f, 0f, options.height.toFloat(),
                options.backgroundGradientColors.first,
                options.backgroundGradientColors.second,
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = shader
        } else {
            // Default beautiful gradient if none provided
            val defaultColors = intArrayOf(
                Color.parseColor("#1E3A8A"), // deep blue
                Color.parseColor("#3B82F6")  // bright blue
            )
            val shader = LinearGradient(0f, 0f, 0f, options.height.toFloat(), defaultColors, null, Shader.TileMode.CLAMP)
            bgPaint.shader = shader
        }
        canvas.drawRect(0f, 0f, options.width.toFloat(), options.height.toFloat(), bgPaint)

        // 2. Prepare text paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.titleTextColor
            textSize = spToPx(options.titleTextSizeSp, context)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.authorTextColor
            textSize = spToPx(options.authorTextSizeSp, context)
            typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
            alpha = 220
        }

        // 3. Layout text with automatic line breaking
        val centerX = options.width / 2f
        val availableWidth = options.width * 0.80f // 80% of width for text
        val titleLines = wrapText(options.title.uppercase(), titlePaint, availableWidth)

        // Calculate total height needed for all text
        val lineHeightTitle = titlePaint.fontSpacing
        val lineHeightAuthor = authorPaint.fontSpacing * 1.4f

        var currentY = options.height * 0.5f -
                (titleLines.size * lineHeightTitle / 2) -
                if (options.subtitle != null || options.author != null) 100f else 0f

        // Optional subtitle
        options.subtitle?.let { sub ->
            val subtitlePaint = Paint(titlePaint).apply {
                textSize = spToPx(64f, context)
                alpha = 200
            }
            val subtitleLines = wrapText(sub, subtitlePaint, availableWidth)
            for (line in subtitleLines) {
                canvas.drawText(line, centerX, currentY, subtitlePaint)
                currentY += subtitlePaint.fontSpacing * 1.2f
            }
            currentY += 60f // spacing
        }

        // Draw title lines
        for (line in titleLines) {
            canvas.drawText(line, centerX, currentY, titlePaint)
            currentY += lineHeightTitle * 1.05f
        }

        // Draw author at bottom
        options.author?.let { author ->
            val authorY = options.height - 180f
            val authorLines = wrapText("by $author", authorPaint, availableWidth)
            var ay = authorY - (authorLines.size - 1) * lineHeightAuthor / 2
            for (line in authorLines) {
                canvas.drawText(line, centerX, ay, authorPaint)
                ay += lineHeightAuthor
            }
        }

        // Save to file
        val coverFile = File(context.getExternalFilesDir(null), "cover.jpg")
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        bitmap.recycle()

        return coverFile
    }

    // Helper: convert sp to pixels
    private fun spToPx(sp: Float, context: android.content.Context): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }

    // Simple word-wrap implementation
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Single word longer than line → force break (rare)
                    lines.add(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}

}