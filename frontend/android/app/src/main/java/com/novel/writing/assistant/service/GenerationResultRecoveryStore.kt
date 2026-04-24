package com.novel.writing.assistant.service

import android.content.Context

object GenerationResultRecoveryStore {
    private const val PREFS_NAME = "generation_result_recovery"
    private const val KEY_PENDING_GENERATION_ID = "pending_generation_id"

    fun stage(context: Context, generationId: String) {
        prefs(context).edit()
            .putString(KEY_PENDING_GENERATION_ID, generationId)
            .apply()
    }

    fun consume(context: Context): String? {
        val generationId = prefs(context)
            .getString(KEY_PENDING_GENERATION_ID, null)
            ?.takeIf { it.isNotBlank() }
        if (!generationId.isNullOrBlank()) {
            clear(context)
        }
        return generationId
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_GENERATION_ID)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
