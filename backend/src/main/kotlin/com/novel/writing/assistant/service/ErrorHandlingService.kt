package com.novel.writing.assistant.service

import java.text.SimpleDateFormat
import java.util.*

object ErrorHandlingService {
    private val errorStats = mutableMapOf<String, Int>()
    
    data class ErrorReport(
        val id: String,
        val timestamp: String,
        val errorType: String,
        val message: String,
        val stackTrace: String,
        val context: Map<String, Any>? = null
    )
    
    /**
     * Handle an exception
     * @param e Exception to handle
     * @param context Optional context information
     * @return Error report
     */
    fun handleException(e: Exception, context: Map<String, Any>? = null): ErrorReport {
        val errorType = e::class.simpleName ?: "UnknownError"
        val message = e.message ?: "No error message"
        val stackTrace = getStackTraceAsString(e)
        
        // Update error stats
        errorStats[errorType] = errorStats.getOrDefault(errorType, 0) + 1
        
        // Log the error
        LogService.error("Error: $message", e)
        
        // Create error report
        val errorReport = ErrorReport(
            id = UUID.randomUUID().toString(),
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date()),
            errorType = errorType,
            message = message,
            stackTrace = stackTrace,
            context = context
        )
        
        // In production, this would be sent to a crash reporting service
        // For now, we'll just log it
        LogService.info("Error report created: ${errorReport.id}")
        
        return errorReport
    }
    
    /**
     * Get stack trace as string
     * @param e Exception
     * @return Stack trace as string
     */
    private fun getStackTraceAsString(e: Exception): String {
        val stackTrace = java.io.StringWriter()
        val printWriter = java.io.PrintWriter(stackTrace)
        e.printStackTrace(printWriter)
        return stackTrace.toString()
    }
    
    /**
     * Get error statistics
     * @return Error statistics map
     */
    fun getErrorStats(): Map<String, Int> {
        return errorStats.toMap()
    }
    
    /**
     * Reset error statistics
     */
    fun resetErrorStats() {
        errorStats.clear()
    }
    
    /**
     * Register global exception handler
     */
    fun registerGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler {
            thread, throwable ->
            val context = mapOf(
                "threadName" to thread.name,
                "threadId" to thread.id
            )
            
            if (throwable is Exception) {
                handleException(throwable, context)
            } else {
                LogService.fatal("Fatal error: ${throwable.message}", throwable as Throwable)
            }
        }
        
        LogService.info("Global exception handler registered")
    }
}