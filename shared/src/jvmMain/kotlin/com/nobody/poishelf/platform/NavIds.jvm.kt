package com.nobody.poishelf.platform

import java.util.UUID

actual fun newNavId(): String = UUID.randomUUID().toString()
