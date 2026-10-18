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
import android.webkit.WebSettings
import timber.log.Timber

/**
 * Reads the Chromium major version of the WebView this process has loaded.
 *
 * Used only to explain a setting to the user. Nothing branches on the result,
 * so a null answer is harmless.
 *
 * The user agent is the only source that works on every supported release.
 * `WebView.getCurrentWebViewPackage()` needs API 26, and the app supports API
 * 21. Chromium's user agent reduction froze the minor parts to `0.0.0`, but the
 * major version is still reported.
 */
object WebViewVersion {

    private val CHROME_MAJOR = Regex("""Chrome/(\d+)""")

    /**
     * The loaded WebView's Chromium major version, or null if it cannot be
     * read.
     *
     * Do not call this from `Application.onCreate`. Reading the default user
     * agent loads the WebView provider, which a GeckoView install would
     * otherwise not need yet.
     */
    fun major(context: Context): Int? {
        try {
            return majorFromUserAgent(WebSettings.getDefaultUserAgent(context))
        } catch (e: Exception) {
            Timber.w(e, "Could not read the WebView user agent")
            return null
        }
    }

    /** Visible for testing. */
    fun majorFromUserAgent(userAgent: String?): Int? {
        if (userAgent == null) {
            return null
        }
        val match = CHROME_MAJOR.find(userAgent)
        if (match == null) {
            return null
        }
        return match.groupValues[1].toIntOrNull()
    }
}
