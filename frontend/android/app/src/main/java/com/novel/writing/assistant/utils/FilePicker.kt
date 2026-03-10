package com.novel.writing.assistant.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class FilePicker(private val activity: ComponentActivity) {
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var onFileSelected: ((File) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    init {
        registerLauncher()
    }
    
    private fun registerLauncher() {
        filePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == ComponentActivity.RESULT_OK) {
                result.data?.data?.let {uri ->
                    try {
                        val file = saveUriToFile(activity, uri)
                        if (validateFile(file)) {
                            onFileSelected?.invoke(file)
                        } else {
                            onError?.invoke("文件验证失败：只支持md/txt格式，大小不超过20MB")
                        }
                    } catch (e: Exception) {
                        onError?.invoke("文件处理失败：${e.message}")
                    }
                }
            }
        }
    }
    
    fun pickFile(
        onFileSelected: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onFileSelected = onFileSelected
        this.onError = onError
        
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/plain", "text/markdown")
            )
        }
        
        filePickerLauncher.launch(intent)
    }
    
    private fun saveUriToFile(context: Context, uri: Uri): File {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: 
            throw IOException("无法打开文件")
        
        val fileName = getFileName(context, uri) ?: "temp_file"
        val fileExtension = getFileExtension(fileName)
        val tempFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "${System.currentTimeMillis()}.$fileExtension"
        )
        
        FileOutputStream(tempFile).use {outputStream ->
            inputStream.copyTo(outputStream)
        }
        
        return tempFile
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        val scheme = uri.scheme
        
        if (scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                }
            }
        } else if (scheme == "file") {
            return File(uri.path).name
        }
        
        return null
    }
    
    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            "txt"
        }
    }
    
    private fun validateFile(file: File): Boolean {
        val extension = getFileExtension(file.name).lowercase()
        val sizeInMB = file.length() / (1024.0 * 1024.0)
        
        return (extension == "txt" || extension == "md" || extension == "markdown") && sizeInMB <= 20
    }
}
