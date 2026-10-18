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

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Removes the Chromium histogram files that WebView leaves inside the app's own
 * data directory.
 *
 * WebView writes one 4 MiB `BrowserMetrics-*.pma` file per browser process
 * session. A `FileMetricsProvider` is meant to read each file and delete it. On
 * WebView 88 and older that provider is not registered when metrics consent is
 * denied, so nothing ever deletes them. WebView 89 added the missing
 * registration.
 *
 * A device with no Play Store update path keeps the old behaviour. On a display
 * that reloads its page often this reaches gigabytes: one measured device held
 * 532 files using 2128 MB, about 42% of its data partition.
 *
 * Every method is safe to call on any version. Nothing throws. A device whose
 * WebView already cleans up has no such directory, so a scan reports nothing
 * and the setting stays hidden.
 *
 * Call [apply] and [clean] off the main thread. Both touch the disk.
 */
object BrowserMetricsCleaner {

    /** Leave the files alone. The default. */
    const val MODE_OFF = "off"

    /** Delete the files on every start. Reclaims space. */
    const val MODE_DELETE = "delete"

    /**
     * Delete the files, then clear the directory's write bit so WebView cannot
     * create another. This also stops the continuous rewriting of the live
     * file, which on one measured device fell from 28 kB/s to 1.8 kB/s.
     */
    const val MODE_DELETE_AND_BLOCK = "delete_and_block"

    private const val SUBDIR = "BrowserMetrics"
    private const val WEBVIEW_DIR = "app_webview"

    /** What a scan or a delete found. */
    data class Usage(val count: Int, val bytes: Long) {
        val megabytes: Long
            get() = bytes / (1024L * 1024L)
    }

    private val NOTHING = Usage(0, 0L)

    /**
     * The directories WebView may keep the files in.
     *
     * Chromium moved the location to the no-backup files directory in late
     * 2025, and migrates in either direction depending on a feature flag, so
     * both are checked. The spare file sits one level above and is left alone:
     * WebView needs it.
     */
    fun directories(context: Context): List<File> {
        val parents = ArrayList<File>(2)
        parents.add(File(context.applicationInfo.dataDir, WEBVIEW_DIR))
        parents.add(context.noBackupFilesDir)
        val directories = ArrayList<File>(parents.size)
        for (parent in parents) {
            directories.add(File(parent, SUBDIR))
        }
        return directories
    }

    /** Counts the files. Deletes nothing. */
    fun scan(context: Context): Usage = scan(directories(context))

    /** Counts the files in [directories]. Visible for testing. */
    fun scan(directories: List<File>): Usage {
        var count = 0
        var bytes = 0L
        for (directory in directories) {
            val files = listFiles(directory)
            if (files == null) {
                continue
            }
            for (file in files) {
                count++
                bytes += file.length()
            }
        }
        return Usage(count, bytes)
    }

    /** Deletes the files and reports what went. Changes no directory mode. */
    fun clean(context: Context): Usage = clean(directories(context))

    /** Deletes the files in [directories]. Visible for testing. */
    fun clean(directories: List<File>): Usage {
        var count = 0
        var bytes = 0L
        for (directory in directories) {
            val removed = prune(directory)
            count += removed.count
            bytes += removed.bytes
        }
        log(count, bytes)
        return Usage(count, bytes)
    }

    /**
     * Applies [mode] and reports what was deleted.
     *
     * A blocked directory has to be made writable again before its contents
     * can be removed, because deleting a file needs write permission on the
     * directory holding it. So each run restores the bit, prunes, then clears
     * the bit again.
     *
     * No directory is created. A device that has never written these files has
     * nothing to block.
     */
    fun apply(context: Context, mode: String): Usage = apply(directories(context), mode)

    /** Applies [mode] to [directories]. Visible for testing. */
    fun apply(directories: List<File>, mode: String): Usage {
        if (mode == MODE_OFF) {
            return NOTHING
        }
        var count = 0
        var bytes = 0L
        for (directory in directories) {
            if (!directory.isDirectory) {
                continue
            }
            setWritable(directory, true)
            val removed = prune(directory)
            count += removed.count
            bytes += removed.bytes
            if (mode != MODE_DELETE_AND_BLOCK) {
                continue
            }
            setWritable(directory, false)
        }
        log(count, bytes)
        return Usage(count, bytes)
    }

    private fun prune(directory: File): Usage {
        val files = listFiles(directory)
        if (files == null) {
            return NOTHING
        }
        var count = 0
        var bytes = 0L
        for (file in files) {
            val size = file.length()
            if (!delete(file)) {
                continue
            }
            count++
            bytes += size
        }
        return Usage(count, bytes)
    }

    private fun listFiles(directory: File): List<File>? {
        try {
            if (!directory.isDirectory) {
                return null
            }
            val entries = directory.listFiles()
            if (entries == null) {
                return null
            }
            val files = ArrayList<File>(entries.size)
            for (entry in entries) {
                if (!entry.isFile) {
                    continue
                }
                files.add(entry)
            }
            return files
        } catch (e: SecurityException) {
            Timber.w(e, "Could not read %s", directory)
            return null
        }
    }

    private fun delete(file: File): Boolean {
        try {
            return file.delete()
        } catch (e: SecurityException) {
            Timber.w(e, "Could not delete %s", file)
            return false
        }
    }

    private fun setWritable(directory: File, writable: Boolean) {
        try {
            directory.setWritable(writable, true)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not set the write bit on %s", directory)
        }
    }

    private fun log(count: Int, bytes: Long) {
        if (count == 0) {
            return
        }
        Timber.d("Removed %d WebView metrics files, %d bytes", count, bytes)
    }
}
