package com.novel.writing.assistant.service

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object LogService {
    private const val LOG_DIR = "logs"
    private const val LOG_FILE_PREFIX = "novel_writing_assistant_"
    private const val LOG_FILE_SUFFIX = ".log"
    
    enum class LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }
    
    init {
        // Create log directory if it doesn't exist
        val logDir = File(LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }
    
    /**
     * Log a message
     * @param level Log level
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun log(level: LogLevel, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())
        val logMessage = "[$timestamp] [${level.name}] $message"
        
        // Print to console
        when (level) {
            LogLevel.ERROR, LogLevel.FATAL -> System.err.println(logMessage)
            else -> System.out.println(logMessage)
        }
        
        // Print stack trace if available
        throwable?.printStackTrace()
        
        // Write to file
        writeToLogFile(logMessage, throwable)
    }
    
    /**
     * Log debug message
     * @param message Log message
     */
    fun debug(message: String) {
        log(LogLevel.DEBUG, message)
    }
    
    /**
     * Log info message
     * @param message Log message
     */
    fun info(message: String) {
        log(LogLevel.INFO, message)
    }
    
    /**
     * Log warning message
     * @param message Log message
     */
    fun warn(message: String) {
        log(LogLevel.WARN, message)
    }
    
    /**
     * Log error message
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun error(message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, message, throwable)
    }
    
    /**
     * Log fatal message
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun fatal(message: String, throwable: Throwable? = null) {
        log(LogLevel.FATAL, message, throwable)
    }
    
    /**
     * Write log message to file
     * @param message Log message
     * @param throwable Optional throwable
     */
    private fun writeToLogFile(message: String, throwable: Throwable? = null) {
        val dateFormat = SimpleDateFormat("yyyyMMdd")
        val logFileName = "$LOG_FILE_PREFIX${dateFormat.format(Date())}$LOG_FILE_SUFFIX"
        val logFile = File(LOG_DIR, logFileName)
        
        try {
            FileWriter(logFile, true).use { writer ->
                writer.write(message)
                writer.write(System.lineSeparator())
                
                // Write stack trace if available
                throwable?.let {
                    val stackTrace = java.io.StringWriter()
                    val printWriter = java.io.PrintWriter(stackTrace)
                    it.printStackTrace(printWriter)
                    writer.write(stackTrace.toString())
                    writer.write(System.lineSeparator())
                }
            }
        } catch (e: IOException) {
            System.err.println("Failed to write to log file: ${e.message}")
        }
    }
    
    /**
     * Get current log file path
     * @return Current log file path
     */
    fun getCurrentLogFilePath(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd")
        val logFileName = "$LOG_FILE_PREFIX${dateFormat.format(Date())}$LOG_FILE_SUFFIX"
        val logFile = File(LOG_DIR, logFileName)
        return logFile.absolutePath
    }
}