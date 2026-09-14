package com.tommasov.mg4simplelauncher.diag;

import android.app.Application;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Installs the crash recorder before anything else runs.
 *
 * <p>The head unit is not reachable over adb, so a crash there leaves no trace unless the app
 * writes one itself. This catches whatever reaches the top of a thread, appends it to the
 * diagnostics log, and then hands the exception on to the default handler so the process
 * still dies the way Android expects — swallowing it would leave the launcher alive in a
 * broken state, which is worse than a restart.
 */
public class LauncherApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticsLog.log(this, "App", DiagnosticsLog.sessionHeader());
        installCrashRecorder();
    }

    private void installCrashRecorder() {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        File logFile = DiagnosticsLog.file(this);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                // Written on this thread, not queued: the process is about to be killed and
                // a background write would never get to run.
                DiagnosticsLog.logNow(logFile, "CRASH",
                        "on thread " + thread.getName() + "\n"
                                + DiagnosticsLog.stackTrace(error));
            } catch (Throwable ignored) {
                // A failure while recording a crash must not replace the crash itself.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }
}
