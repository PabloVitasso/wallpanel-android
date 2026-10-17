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

package xyz.wallpanel.pro

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import timber.log.Timber
import xyz.wallpanel.pro.persistence.ScheduleRepository
import xyz.wallpanel.pro.ui.activities.BrowserActivityNative
import xyz.wallpanel.pro.utils.AppRestartHelper
import xyz.wallpanel.pro.utils.BootReadinessGate
import xyz.wallpanel.pro.utils.OverlayPermission
import xyz.wallpanel.pro.utils.ScheduledTaskAlarmScheduler

class BootUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) {
            return
        }
        val appContext = context.applicationContext
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)

        // AlarmManager drops every alarm on reboot, so the scheduled tasks are armed again
        // whether or not the application itself opens on boot.
        ScheduledTaskAlarmScheduler.scheduleAll(appContext, ScheduleRepository(appContext, sharedPreferences))

        val startOnBoot = sharedPreferences.getBoolean(context.getString(R.string.key_setting_android_startonboot), false)
        if (!startOnBoot) {
            return
        }
        // From Android 15 the system also sends BOOT_COMPLETED to an application coming out
        // of the force-stopped state, which is to say when someone opens it again. That is no
        // boot: the browser is already being opened, and a second start would stack a second
        // browser on it. Earlier versions have no such case.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            startedFromForceStop(appContext)
        ) {
            Timber.i("BOOT_COMPLETED after a force stop, the application is being opened")
            return
        }
        Timber.i("Opening the browser on boot (${intent.action})")
        if (OverlayPermission.isGranted(appContext)) {
            // Below API 29, or with "display over other apps" granted, a receiver may start
            // the browser itself.
            // Brings a browser that is already open to the front rather than adding another
            val browser = Intent(appContext, BrowserActivityNative::class.java)
            browser.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            try {
                appContext.startActivity(browser)
                return
            } catch (e: Exception) {
                Timber.e(e, "Unable to open the browser on boot")
            }
        }
        // Otherwise Android drops a start from the background without a word. The relaunch
        // the restarts use still works: its full screen intent opens the browser on a locked
        // or sleeping screen, which is where a device usually is just after booting, and
        // leaves a notification to tap otherwise.
        AppRestartHelper.relaunchBrowser(appContext)
    }

    /**
     * Whether this process started with the application leaving the force-stopped state.
     * A real boot can deliver BOOT_COMPLETED late, held back until the first unlock on an
     * encrypted device with a PIN, so uptime alone cannot tell the two apart. Without a
     * start record for this process, a BOOT_COMPLETED well past the boot is taken for one.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startedFromForceStop(context: Context): Boolean {
        try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            // Newest first, and the receiver runs in the main process, named after the
            // package. Matching on the pid does not work: the records carry none.
            val start = activityManager?.getHistoricalProcessStartReasons(START_RECORDS)
                ?.firstOrNull { it.processName == context.packageName }
            if (start != null) {
                Timber.d("Process start reason ${start.reason}, force stopped ${start.wasForceStopped()}")
                return start.wasForceStopped()
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Unable to read how the process started")
        }
        return SystemClock.elapsedRealtime() > BootReadinessGate.BOOT_WINDOW_MS
    }

    companion object {
        // GeckoView's content, GPU and crash helper processes are in the same list, and
        // each restart of one adds a record ahead of the main process
        private const val START_RECORDS = 50

        /**
         * Boot completed, and the quick boot broadcasts some devices (HTC, some Fire and
         * vendor builds) send in its place when they come back from a fast power off.
         */
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
