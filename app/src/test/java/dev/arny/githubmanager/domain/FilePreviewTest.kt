package dev.arny.githubmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePreviewTest {
    @Test fun kindsByExtensionAndContent() {
        assertEquals(FilePreviewKind.MARKDOWN, previewKindFor("README.MD", ByteArray(0)))
        assertEquals(FilePreviewKind.IMAGE, previewKindFor("icon.png", ByteArray(4)))
        assertEquals(FilePreviewKind.VECTOR, previewKindFor("logo.svg", ByteArray(4)))
        assertEquals(FilePreviewKind.BINARY, previewKindFor("app.bin", byteArrayOf(0, 1, 2)))
        assertEquals(FilePreviewKind.TEXT, previewKindFor("Main.kt", "fun main() {}".toByteArray()))
    }

    @Test fun binaryDetectedByNulByte() {
        assertTrue(looksBinary(byteArrayOf(0x50, 0x4b, 0x00, 0x04)))
        assertFalse(looksBinary("plain text".toByteArray()))
        assertFalse(looksBinary(ByteArray(0)))
    }

    @Test fun hexDumpLayout() {
        val dump = hexDump(byteArrayOf(0x41, 0x42, 0x00, 0x43), maxBytes = 16)
        val lines = dump.lines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("00000000  41 42 00 43 "))
        assertTrue(lines[0].endsWith("|AB.C|"))
    }

    @Test fun hexDumpTruncates() {
        val dump = hexDump(ByteArray(40) { 0x41 }, maxBytes = 16)
        assertTrue(dump.lines().size == 2)
        assertTrue(dump.contains("… ещё 24 байт не показано"))
    }

    @Test fun hexDumpEmpty() {
        assertEquals("", hexDump(ByteArray(0)))
    }
}
