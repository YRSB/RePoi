package com.nobody.repoi.platform

import java.util.UUID

actual fun newNavId(): String = UUID.randomUUID().toString()
