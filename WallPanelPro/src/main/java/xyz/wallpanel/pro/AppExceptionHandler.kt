/*
 * Copyright (c) 2022 Wallpanel
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

package xyz.wallpanel.pro

import android.content.Context
import timber.log.Timber
import xyz.wallpanel.pro.utils.AppRestartHelper

/**
 * Restarts the application after an uncaught exception. It is the process-wide default
 * handler, so it holds the application context only: holding an activity kept every browser
 * activity it was installed from, with its WebView or GeckoView, alive for good.
 */
class AppExceptionHandler(context: Context) : Thread.UncaughtExceptionHandler {

    private val appContext = context.applicationContext

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Timber.e(ex, "Uncaught exception on thread ${thread.name}, restarting the application")
        AppRestartHelper.restartApplication(appContext)
    }
}