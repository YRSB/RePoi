package com.nobody.poishelf.platform

/**
 * 日志文件分享（S3）：将导出的日志字节以 `fileName` 落盘并调起系统分享。
 */
expect suspend fun shareLogFile(fileName: String, text: String)
