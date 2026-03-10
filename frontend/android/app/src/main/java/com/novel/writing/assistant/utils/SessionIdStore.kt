package com.novel.writing.assistant.utils

import android.content.Context

class SessionIdStore(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("novel-writing-assistant", Context.MODE_PRIVATE)

    fun saveSessionId(projectId: String, sessionId: String) {
        sharedPreferences.edit()
            .putString(key(projectId), sessionId)
            .apply()
    }

    fun getSessionId(projectId: String): String? {
        return sharedPreferences.getString(key(projectId), null)
    }

    fun clearSessionId(projectId: String) {
        sharedPreferences.edit()
            .remove(key(projectId))
            .apply()
    }

    private fun key(projectId: String): String = "sessionId:$projectId"
}
