package com.nobody.repoi.platform

expect fun cacheDirPath(): String
expect fun filesDirPath(): String

expect suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit>
