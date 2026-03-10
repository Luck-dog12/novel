package com.novel.writing.assistant.service

class MissingSessionIdException : RuntimeException("Missing sessionId for continuation")

class NoContextException : RuntimeException("No context available for continuation")
