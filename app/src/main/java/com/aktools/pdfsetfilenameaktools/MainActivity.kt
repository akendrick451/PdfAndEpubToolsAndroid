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
import androidx.documentfile.provider.DocumentFile
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
import nl.siegmann.epublib.service.MediatypeService
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// checking an update for git
// issue where the table of contents is lost when this is run 
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================
// RUN THIS UIN ANDROID ANDROID ANDROID ANDROID STUDIO! ========================================================================

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickPDFForFilename: Button
    private lateinit var btnPickPDFForTitle: Button

    private lateinit var txtVersion: TextView

    private lateinit var txtStatus: TextView
    private lateinit var pdfPickerLauncherForFilename: ActivityResultLauncher<Intent>
    private lateinit var pdfPickerLauncherForTitleChange : ActivityResultLauncher<Intent>
    private val PICK_EPUB_REQUEST = 1001

    public val appVersion= "v11.0 25 May 2026"

    private lateinit var btnPickEpub: Button

     override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize PDFBox once
        PDFBoxResourceLoader.init(applicationContext)


        btnPickPDFForFilename = findViewById(R.id.btnPickPDFForFilename)
         btnPickPDFForTitle = findViewById(R.id.btnPickPDFForTitle)
         txtStatus = findViewById(R.id.text_status)
         txtVersion = findViewById(R.id.text_version)
         txtVersion.text = appVersion



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



    // Safe way - returns File only if possible, otherwise null
    private fun getFileFromUri(uri: Uri?): File? {
        // Try to get real path (works in some cases)
        if ( uri == null ) {
            return null
        }  else {
            val filePath = getRealPathFromUri(uri)
            if (filePath != null) {
                return File(filePath)
            }
        }
        // Fallback: Create temp file by copying content
        return copyUriToTempFile(uri)
    }

    // Helper to get real path (limited success)
    private fun getRealPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else null
        }
    }

    // Copy to cache as File (Most reliable)
    private fun copyUriToTempFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "epub_${System.currentTimeMillis()}.epub")
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
                normalizeGoogleDocsEpub(tempEpub, title)

                // readEpub later seems to lose toc.ncx                  .xhml, get a handle on it here
                val fileTocNCX = getTocNCX(tempEpub)
                val fileTocNCXSaved = File(cacheDir, "nav_${System.currentTimeMillis()}.toc")
                fileTocNCX.copyTo(fileTocNCXSaved, overwrite = true)

                val fileNavXhtml = getNavXhtml(tempEpub)
                val fileNavXhtmlSaved = File(cacheDir, "nav_${System.currentTimeMillis()}.xhtml")
                fileNavXhtml.copyTo(fileNavXhtmlSaved, overwrite = true)
                // NEED TO COPY THIS IS TEMP EPUB IS DELETED!!! - maybe just delte it later?

                // ── Read safely (your helper function) ────────────────────────────
                val book = try {
                    val loadedBook = tempEpub.readEpubSafely()

                    // Force initialization to silence NCX warning
                    loadedBook.tableOfContents

                    loadedBook   // explicitly return the book
                } catch (e: Exception) {
                    tempEpub.delete()
                    throw IllegalStateException("Not a valid EPUB (DRM? Corrupted? Not an EPUB?)", e)
                }

                // try to preserve table of contents
                // === ADD THIS BLOCK ===
              //  rebuildTableOfContents(book)   // ← Add this

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

               // val newEpubUri = saveEpubWithNewName(book, epubUri, "")
                saveToActualLocation(book, epubUri)


                // manually add toc.ncx which seems to get lost (may need to add to content file also

                val fileEpub = getFileFromUri(epubUri)
               // addTocNcxToEpub(fileEpub, fileTocNCX)
                if (fileEpub != null) {
                    addNavXhtmlToEpub(fileEpub, fileNavXhtmlSaved)

                }

                addTocNcxToEpub(fileEpub, fileTocNCXSaved)

                // Step 3: Write modified file back to original Uri
                if (fileEpub != null) {
                    writeFileBackToUri(fileEpub, epubUri)
                }   // Wait, we need to rename first



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

    // Write File → Uri
    private fun writeFileBackToUri(sourceFile: File, targetUri: Uri) {
        contentResolver.openOutputStream(targetUri, "wt")?.use { os ->
            sourceFile.inputStream().use { input ->
                input.copyTo(os)
            }
        } ?: throw IllegalStateException("Cannot write to Uri")
    }
    fun saveToActualLocation(book: Book, epubUri: Uri) {

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
    }

    fun saveEpubWithNewName(
        book: Book,
        originalUri: Uri,
        suffix: String = "_withCover"
    ): Uri? {

        try {
            val originalName = getFileNameFromUri(originalUri) ?: "book"
            val baseName = originalName.removeSuffix(".epub" )
            val newFileName = "${baseName}${suffix}.epub"

            println("Trying to create: $newFileName")

            // Try to create new file
            val newUri = createNewFileInSameFolder(originalUri, newFileName)

            if (newUri == null) {
                println("❌ Failed to create new file. Trying alternative method...")
                return saveUsingTempFileMethod(book, originalUri, newFileName)
            }

            // Write the EPUB
            writeBookToUri(book, newUri)

            println("✅ Successfully saved: $newFileName")
            return newUri

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error: ${e.message}")
            return null
        }
    }

    // Alternative method (more reliable for Downloads folder)
    private fun saveUsingTempFileMethod(book: Book, originalUri: Uri, newFileName: String): Uri? {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
        }

        val newUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        writeBookToUri(book, newUri)
        return newUri
    }

    private fun writeBookToUri(book: Book, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            contentResolver.openOutputStream(uri, "wt")?.use { os ->
                nl.siegmann.epublib.epub.EpubWriter().write(book, os)
            }
        } else {
            val tempFile = File(cacheDir, "temp_epub_${System.currentTimeMillis()}.epub")
            try {
                nl.siegmann.epublib.epub.EpubWriter().write(book, tempFile.outputStream())
                contentResolver.openOutputStream(uri, "wt")?.use { os ->
                    tempFile.inputStream().use { it.copyTo(os) }
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    // Create new file in the same directory
    private fun createNewFileInSameFolder(originalUri: Uri, newFileName: String): Uri? {
        val documentFile = DocumentFile.fromSingleUri(this, originalUri) ?: return null
        val parent = documentFile.parentFile ?: return null

        // Delete if file with same name already exists
        parent.findFile(newFileName)?.delete()

        return parent.createFile("application/epub+zip", newFileName)?.uri
    }
    fun addFileToEpub(epubFile: File?, fileToAdd: File, strFileNameToAdd: String) {
        epubFile?.exists()?.let {
            if (!it) {
                println("❌ EPUB file not found: $epubFile")
                return
            }
        }
        if (!fileToAdd.exists()) {
            println("❌ Source file not found: $fileToAdd")
            return
        }

        val tempFile = File(epubFile?.parentFile, "${epubFile?.name}.tmp")

        // Ensure the target file name has the OEBPS/ prefix
        // If it already starts with "OEBPS/", use it as is; otherwise, prepend it.
        val targetZipPath = if (strFileNameToAdd.startsWith("OEBPS/", ignoreCase = true)) {
            strFileNameToAdd
        } else {
            "OEBPS/$strFileNameToAdd"
        }

        try {
            ZipFile(epubFile).use { zipIn ->
                ZipOutputStream(FileOutputStream(tempFile)).use { zipOut ->

                    // Copy all existing entries
                    zipIn.entries().asSequence().forEach { entry ->
                        zipIn.getInputStream(entry).use { input ->

                            // Check if this existing entry matches our target path to replace it
                            if (entry.name.equals(targetZipPath, ignoreCase = true)) {
                                return@forEach  // Skip old file to allow replacement
                            }

                            val newEntry = ZipEntry(entry.name)
                            zipOut.putNextEntry(newEntry)
                            input.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }

                    // Add the new file to the specific OEBPS path
                    val fileBytes = fileToAdd.readBytes()
                    val ncxEntry = ZipEntry(targetZipPath).apply {
                        method = ZipEntry.DEFLATED
                    }

                    zipOut.putNextEntry(ncxEntry)
                    zipOut.write(fileBytes)
                    zipOut.closeEntry()
                }
            }

            // Replace original with updated version
            if (epubFile?.delete() == true && tempFile.renameTo(epubFile)) {
                println("✅ Successfully added/replaced $targetZipPath in ${epubFile.name}")
            } else {
                println("❌ Failed to replace original EPUB")
            }

        } catch (e: Exception) {
            println("❌ Error while adding file: ${e.message}")
            tempFile.delete()
        }
    }

    fun addTocNcxToEpub(epubFile: File?, tocNcxFile: File) {
      addFileToEpub(epubFile, tocNcxFile, "toc.ncx")
    }

    fun addNavXhtmlToEpub(epubFile: File, navFile: File) {
        if (!epubFile.exists() || !navFile.exists()) {
            println("❌ Required file not found")
            return
        }

        val tempFile = File(epubFile.parentFile, "${epubFile.name}.tmp")

        try {
            var usesOebps = false
            var opfPath = "content.opf"

            ZipFile(epubFile).use { zipIn ->
                // Detect structure
                zipIn.entries().asSequence().forEach { entry ->
                    if (entry.name.equals("OEBPS/content.opf", ignoreCase = true)) {
                        usesOebps = true
                        opfPath = "OEBPS/content.opf"
                    }
                }

                println("📁 EPUB structure detected: ${if (usesOebps) "OEBPS folder" else "Flat (root)"}")

                ZipOutputStream(FileOutputStream(tempFile)).use { zipOut ->

                    zipIn.entries().asSequence().forEach { entry ->
                        zipIn.getInputStream(entry).use { input ->
                            val entryName = entry.name

                            when {
                                // Update content.opf
                                entryName.equals(opfPath, ignoreCase = true) -> {
                                    val opfContent = input.readBytes().toString(Charsets.UTF_8)
                                    val updatedOpf = updateOpfWithNav(opfContent, usesOebps)

                                    zipOut.putNextEntry(ZipEntry(entryName))
                                    zipOut.write(updatedOpf.toByteArray(Charsets.UTF_8))
                                    zipOut.closeEntry()
                                }

                                // Skip existing nav.xhtml
                                entryName.equals("nav.xhtml", ignoreCase = true) ||
                                        entryName.equals("OEBPS/nav.xhtml", ignoreCase = true) -> {
                                    return@forEach
                                }

                                else -> {
                                    zipOut.putNextEntry(ZipEntry(entryName))
                                    input.copyTo(zipOut)
                                    zipOut.closeEntry()
                                }
                            }
                        }
                    }

                    // Add nav.xhtml in correct location
                    val navEntryPath = if (usesOebps) "OEBPS/nav.xhtml" else "nav.xhtml"

                    val navBytes = navFile.readBytes()
                    val navEntry = ZipEntry(navEntryPath).apply {
                        method = ZipEntry.DEFLATED
                    }

                    zipOut.putNextEntry(navEntry)
                    zipOut.write(navBytes)
                    zipOut.closeEntry()

                    println("✅ nav.xhtml added at: $navEntryPath")
                }
            }

            // Replace original
            if (epubFile.delete() && tempFile.renameTo(epubFile)) {
                println("✅ EPUB successfully updated with nav.xhtml")
            } else {
                println("❌ Failed to replace original EPUB")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
        }
    }

    private fun updateOpfWithNav(opfContent: String, usesOebps: Boolean): String {
        var result = opfContent
        val navHref = if (usesOebps) "nav.xhtml" else "nav.xhtml"

        if (result.contains("<opf:manifest")) {
            // Namespaced
            if (!result.contains("id=\"nav\"")) {
                result = result.replace(
                    "</opf:manifest>",
                    """<opf:item id="nav" href="$navHref" media-type="application/xhtml+xml" properties="nav"/></opf:manifest>"""
                )
            }
        } else if (result.contains("<manifest")) {
            // Non-namespaced
            if (!result.contains("id=\"nav\"")) {
                result = result.replace(
                    "</manifest>",
                    """<item id="nav" href="$navHref" media-type="application/xhtml+xml" properties="nav"/></manifest>"""
                )
            }
        } else {
            println("⚠️ Could not find manifest tag in content.opf")
        }

        return result
    }

    private fun File.readEpubSafely( ): Book {
        require(exists() && length() > 0L) { "EPUB file is missing or empty" }

        val epubReader = EpubReader()

        return this.inputStream().use { inputStream ->
            try {
                // Prefer full read over lazy for better resource loading
                val book = epubReader.readEpub(inputStream)

                Log.d("EPUB", "Loaded with readEpub() - Resources: ${book.resources.all.size}")

                // Force load ALL resources (important for nav.xhtml, toc.ncx, etc.)
                book.resources.all.forEach { resource ->
                    try {
                        if (resource.data == null || resource.data.isEmpty()) {
                            // This triggers actual loading of the resource data
                            resource.inputStream.use { it.readBytes() }
                        }
                    } catch (e: Exception) {
                        Log.w("EPUB", "Failed to preload resource: ${resource.href}", e)
                    }
                }

                // Create Resource from file



                // Extra: explicitly look for nav.xhtml after loading
                val nav = book.resources.getByHref("nav.xhtml")
                    ?: book.resources.getByHref("OEBPS/nav.xhtml")
                    ?: book.resources.all.firstOrNull { it.href?.contains("nav.xhtml", true) == true }

                Log.d("EPUB", "nav.xhtml found after loading: ${nav != null} | href=${nav?.href}")

                book

            } catch (e: Exception) {
                Log.w("EPUB", "Full readEpub failed, trying fallback", e)

                // Fallback
                val book = epubReader.readEpubLazy(this.absolutePath, "UTF-8")

                // Force resource initialization
                book.resources.all.forEach { it.data }

                book
            }
        }
    }

    private fun getNavXhtml(epubFile: File): File {

        val extractDir = File(epubFile.parent, "extracted_${System.currentTimeMillis()}")
        unzip(epubFile, extractDir)
        val oebpsFolder = File(extractDir, "OEBPS")
        val fileNavXhtml = File(oebpsFolder, "nav.xhtml")
        return fileNavXhtml

    } // end function getNavXhtml

    private fun getTocNCX(epubFile: File): File {

        val extractDir = File(epubFile.parent, "extracted_${System.currentTimeMillis()}")
        unzip(epubFile, extractDir)
        val oebpsFolder = File(extractDir, "OEBPS")
        val tocNCX = File(oebpsFolder, "toc.ncx")
        return tocNCX

    } // end function getNavXhtml

    private fun normalizeGoogleDocsEpub(tempEpub: File, bookTitle: String = "A Book Title" ): Boolean {

        try {
            // Extract the EPUB
            val extractDir = File(tempEpub.parent, "extracted_${System.currentTimeMillis()}")
            unzip(tempEpub, extractDir)

            // Find the weird GoogleDoc folder
            val googleDocFolder = extractDir.listFiles()?.firstOrNull {
                it.isDirectory && it.name.contains("GoogleDoc", ignoreCase = true)
            }

            val metaInfFolder = extractDir.listFiles()?.firstOrNull {
                it.isDirectory && it.name.contains("META-INF", ignoreCase = true)
            }

            if (googleDocFolder != null && googleDocFolder.name != "OEBPS") {
                val newOebpsFolder = File(extractDir, "OEBPS")

                if (googleDocFolder.renameTo(newOebpsFolder)) {
                    Log.d("EPUB", "Renamed ${googleDocFolder.name} → OEBPS")

                    // Update container.xml to point to new location
                    updateContainerXml(extractDir)
                    // rename the weird google doc filename to content.xml
                  //  renameWeirdFileNameToContentXML(newOebpsFolder)


                    val navFile = File(newOebpsFolder, "nav.xhtml")           // or wherever it is
                    val ncxFile = File(newOebpsFolder, "toc.ncx")
                    //for older versions build v2 epub table of contents
                    generateTocNcxFromNav(navFile, ncxFile, bookTitle )

                    // Re-zip the EPUB
                    zipFolder(extractDir, tempEpub)

                    // Clean up
                    extractDir.deleteRecursively()
                    return true
                }
            } else {
                extractDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("EPUB", "Failed to normalize folder structure", e)
        }
        return false
    } // end fun normalize google doc

    private fun unzip(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun zipFolder(sourceDir: File, targetZip: File) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(targetZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val zipEntry = java.util.zip.ZipEntry(file.relativeTo(sourceDir).path)
                    zos.putNextEntry(zipEntry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun updateContainerXml(extractDir: File) {
        val containerFile = File(extractDir, "META-INF/container.xml")
        if (containerFile.exists()) {
            var content = containerFile.readText()
            // Update rootfile path if it points to GoogleDoc
            content = content.replace(
                Regex("""full-path="[^"]*GoogleDoc/[^"]*content\.opf"""", RegexOption.IGNORE_CASE),
                """full-path="OEBPS/content.opf""""
            )
            content = content.replace(
                Regex("""full-path="[^"]*GoogleDoc/[^"]*package\.opf"""", RegexOption.IGNORE_CASE),
                """full-path="OEBPS/package.opf""""
            )
            containerFile.writeText(content)
        }
    }

    private fun renameWeirdFileNameToContentXML(extractDir: File) {
        if (!extractDir.isDirectory) return

        val xhtmlFiles = extractDir.listFiles { _, name ->
            name.lowercase().endsWith(".xhtml")
        }?.filter { it.name.lowercase() != "nav.xhtml" } ?: emptyList()

        xhtmlFiles.forEach { file ->
            val target = File(extractDir, "content.xhtml")

            if (target.exists()) {
                println("content.xhtml already exists. Skipping rename of ${file.name}")
                return@forEach
            }

            if (file.renameTo(target)) {
                println("Successfully renamed ${file.name} to content.xhtml")
            } else {
                println("Failed to rename ${file.name} to content.xhtml")
            }
        }
    }


    private fun generateTocNcxFromNav(navFile: File, outputNcxFile: File, bookTitle: String = "Book Title") {
        if (!navFile.exists()) {
            println("❌ nav.xhtml not found at: ${navFile.absolutePath}")
            return
        }

        val content = navFile.readText()

        // Extract all <a href="...">...</a> links from the TOC
        val linkRegex = Regex("""<a\s+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)

        val entries = linkRegex.findAll(content)
            .map { match ->
                val href = match.groupValues[1].trim()
                val text = match.groupValues[2]
                    .replace(Regex("<.*?>"), "")   // remove nested HTML tags
                    .trim()
                Pair(text, href)
            }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .toList()

        if (entries.isEmpty()) {
            println("⚠️ No TOC entries found in nav.xhtml")
            return
        }

        val ncxContent = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1" xml:lang="en">""")
            appendLine("  <head>")
            appendLine("""    <meta name="dtb:uid" content="${bookTitle.hashCode()}" />""")
            appendLine("    <meta name=\"dtb:depth\" content=\"3\" />")
            appendLine("    <meta name=\"dtb:totalPageCount\" content=\"0\" />")
            appendLine("    <meta name=\"dtb:maxPageNumber\" content=\"0\" />")
            appendLine("  </head>")
            appendLine("  <docTitle>")
            appendLine("    <text>$bookTitle</text>")
            appendLine("  </docTitle>")
            appendLine("  <navMap>")

            entries.forEachIndexed { index, (text, href) ->
                val playOrder = index + 1
                appendLine("""    <navPoint id="navPoint$playOrder" playOrder="$playOrder">""")
                appendLine("      <navLabel>")
                appendLine("        <text>$text</text>")
                appendLine("      </navLabel>")
                appendLine("""      <content src="$href" />""")
                appendLine("    </navPoint>")
            }

            appendLine("  </navMap>")
            appendLine("</ncx>")
        }

        outputNcxFile.writeText(ncxContent, Charsets.UTF_8)
        println("✅ Successfully created toc.ncx with ${entries.size} entries")
        println("   nav.xhtml was kept untouched")
    } // end function rebuildTableOfContents





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


        private fun getRandomHarmoniousColors(): Pair<Int, Int> {
            val random = java.util.Random()
            val baseHue = random.nextInt(360)

            val hue1 = (baseHue).mod(360)
            val hue2 = (baseHue + 45 + random.nextInt(50)).mod(360)  // nice separation

            val saturation = 75 + random.nextInt(20)
            val brightness1 = 80 + random.nextInt(18)
            val brightness2 = (brightness1 - 12).coerceAtLeast(55)

            val color1 = android.graphics.Color.HSVToColor(floatArrayOf(hue1.toFloat(), saturation/100f, brightness1/100f))
            val color2 = android.graphics.Color.HSVToColor(floatArrayOf(hue2.toFloat(), saturation/100f, brightness2/100f))

            return Pair(color1, color2)
        }

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
            val (color1, color2) = getRandomHarmoniousColors()
            val shader = LinearGradient(0f, 0f, 0f, options.height.toFloat(), color1,color2 , Shader.TileMode.CLAMP)
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