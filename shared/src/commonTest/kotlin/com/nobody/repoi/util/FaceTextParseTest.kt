package com.nobody.repoi.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceTextParseTest {

    @Test
    fun validArray_parsesItems() {
        val items = parseFaceTexts("""[{"id":"smile","content":"^_^"},{"id":"cry","content":"T_T"}]""")
        assertEquals(2, items.size)
        assertEquals("smile", items[0].id)
        assertEquals("^_^", items[0].content)
    }

    @Test
    fun malformedJson_returnsEmpty() {
        assertTrue(parseFaceTexts("not-json").isEmpty())
        assertTrue(parseFaceTexts("").isEmpty())
        assertTrue(parseFaceTexts("{}").isEmpty())
    }
}
