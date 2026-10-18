/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.pro.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class BrowserMetricsCleanerTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `scan reports nothing when the directory is absent`() {
        val usage = BrowserMetricsCleaner.scan(listOf(File(folder.root, "missing")))

        assertEquals(0, usage.count)
        assertEquals(0L, usage.bytes)
    }

    @Test
    fun `scan counts the files and their bytes`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 1024)
        write(directory, "BrowserMetrics-B.pma", 2048)

        val usage = BrowserMetricsCleaner.scan(listOf(directory))

        assertEquals(2, usage.count)
        assertEquals(3072L, usage.bytes)
    }

    @Test
    fun `scan adds up both candidate directories`() {
        val first = folder.newFolder("app_webview", "BrowserMetrics")
        val second = folder.newFolder("no_backup", "BrowserMetrics")
        write(first, "BrowserMetrics-A.pma", 1024)
        write(second, "BrowserMetrics-B.pma", 1024)

        val usage = BrowserMetricsCleaner.scan(listOf(first, second))

        assertEquals(2, usage.count)
        assertEquals(2048L, usage.bytes)
    }

    @Test
    fun `scan ignores a nested directory`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 512)
        File(directory, "nested").mkdir()

        assertEquals(1, BrowserMetricsCleaner.scan(listOf(directory)).count)
    }

    @Test
    fun `scan deletes nothing`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 512)

        BrowserMetricsCleaner.scan(listOf(directory))

        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test
    fun `clean deletes every file and reports the bytes freed`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 4096)
        write(directory, "BrowserMetrics-B.pma", 4096)

        val removed = BrowserMetricsCleaner.clean(listOf(directory))

        assertEquals(2, removed.count)
        assertEquals(8192L, removed.bytes)
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test
    fun `clean leaves the spare file in the parent directory alone`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 4096)
        val spare = write(folder.root, "BrowserMetrics-spare.pma", 4096)

        BrowserMetricsCleaner.clean(listOf(directory))

        assertTrue("WebView needs the spare file", spare.exists())
    }

    @Test
    fun `clean survives an absent directory`() {
        val present = folder.newFolder("BrowserMetrics")
        write(present, "BrowserMetrics-A.pma", 1024)

        val removed = BrowserMetricsCleaner.clean(
            listOf(File(folder.root, "missing"), present)
        )

        assertEquals(1, removed.count)
    }

    @Test
    fun `off mode changes nothing`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 1024)

        val removed = BrowserMetricsCleaner.apply(
            listOf(directory), BrowserMetricsCleaner.MODE_OFF
        )

        assertEquals(0, removed.count)
        assertEquals(1, directory.listFiles()!!.size)
        assertTrue(ownerCanWrite(directory))
    }

    @Test
    fun `delete mode empties the directory and leaves it writable`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 2048)

        val removed = BrowserMetricsCleaner.apply(
            listOf(directory), BrowserMetricsCleaner.MODE_DELETE
        )

        assertEquals(1, removed.count)
        assertEquals(0, directory.listFiles()!!.size)
        assertTrue("WebView must still be able to write", ownerCanWrite(directory))
    }

    @Test
    fun `block mode empties the directory and clears its write bit`() {
        val directory = folder.newFolder("BrowserMetrics")
        write(directory, "BrowserMetrics-A.pma", 2048)

        BrowserMetricsCleaner.apply(
            listOf(directory), BrowserMetricsCleaner.MODE_DELETE_AND_BLOCK
        )

        assertEquals(0, directory.listFiles()!!.size)
        assertFalse(ownerCanWrite(directory))
    }

    @Test
    fun `block mode can still prune on a later run`() {
        val directory = folder.newFolder("BrowserMetrics")
        BrowserMetricsCleaner.apply(
            listOf(directory), BrowserMetricsCleaner.MODE_DELETE_AND_BLOCK
        )
        // A file that arrived before the bit was cleared, or while another
        // provider still held the directory open.
        directory.setWritable(true, true)
        write(directory, "BrowserMetrics-B.pma", 1024)
        directory.setWritable(false, false)

        val removed = BrowserMetricsCleaner.apply(
            listOf(directory), BrowserMetricsCleaner.MODE_DELETE_AND_BLOCK
        )

        assertEquals("the run must restore the write bit before pruning", 1, removed.count)
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test
    fun `block mode creates no directory`() {
        val absent = File(folder.root, "missing")

        BrowserMetricsCleaner.apply(
            listOf(absent), BrowserMetricsCleaner.MODE_DELETE_AND_BLOCK
        )

        assertEquals(false, absent.exists())
    }

    @Test
    fun `usage converts bytes to whole megabytes`() {
        assertEquals(0L, BrowserMetricsCleaner.Usage(1, 1024L).megabytes)
        assertEquals(4L, BrowserMetricsCleaner.Usage(1, 4L * 1024 * 1024).megabytes)
        assertEquals(2128L, BrowserMetricsCleaner.Usage(532, 532L * 4 * 1024 * 1024).megabytes)
    }

    @Test
    fun `version reads the major from a WebView user agent`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 7.1.2; Nexus 7 Build/NJH47F; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/87.0.4280.101 Mobile Safari/537.36"

        assertEquals(87, WebViewVersion.majorFromUserAgent(userAgent))
    }

    @Test
    fun `version reads the major when the minor parts are frozen`() {
        assertEquals(
            156,
            WebViewVersion.majorFromUserAgent("... Chrome/156.0.0.0 Mobile Safari/537.36")
        )
    }

    @Test
    fun `version is absent when the user agent carries none`() {
        assertNull(WebViewVersion.majorFromUserAgent(null))
        assertNull(WebViewVersion.majorFromUserAgent(""))
        assertNull(WebViewVersion.majorFromUserAgent("Mozilla/5.0 Gecko/20100101 Firefox/144.0"))
    }

    /**
     * Reads the owner write bit instead of asking [File.canWrite].
     *
     * `canWrite` reports the effective permission. Tests run as root inside the
     * build container, and root ignores the mode, so `canWrite` is true even on
     * a directory with no permissions at all. The mode bit is what the cleaner
     * actually changes, so that is what the test checks.
     */
    private fun ownerCanWrite(directory: File): Boolean {
        val permissions = Files.getPosixFilePermissions(directory.toPath())
        return permissions.contains(PosixFilePermission.OWNER_WRITE)
    }

    private fun write(parent: File, name: String, size: Int): File {
        val file = File(parent, name)
        file.writeBytes(ByteArray(size))
        return file
    }
}
