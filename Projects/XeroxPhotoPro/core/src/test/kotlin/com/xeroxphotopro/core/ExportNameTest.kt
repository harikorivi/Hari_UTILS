package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test

class ExportNameTest {
    @Test fun namesAreReadableAndCannotBecomePaths() {
        assertEquals("XeroxPhotoPro-Hari-Korivi-",ExportName.prefix(" Hari  Korivi "))
        assertEquals("XeroxPhotoPro-",ExportName.prefix("  "))
        assertEquals("XeroxPhotoPro-",ExportName.prefix("../\\:*?\"<>|"))
        assertEquals("XeroxPhotoPro-రవి-",ExportName.prefix("రవి"))
        assertFalse(ExportName.prefix("A/B\\C\nD").contains('/'))
        assertTrue(ExportName.prefix("న".repeat(100)).toByteArray().size < 220)
    }
}
