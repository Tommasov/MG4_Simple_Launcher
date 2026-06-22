package com.tommasov.mg4simplelauncher.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Verifies a downloaded APK and hands it to the system package installer. */
public final class ApkInstaller {

    private static final String TAG = "ApkInstaller";

    private ApkInstaller() {
    }

    /** True once the user has granted this app permission to install packages. */
    public static boolean canInstall(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /**
     * Sends the user to the system screen where they can allow this app to install
     * unknown apps. They return to the launcher afterwards; re-check {@link #canInstall}.
     */
    public static void requestInstallPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /** Computes the lowercase hex SHA-256 of a file. */
    @NonNull
    public static String sha256(@NonNull File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** True if the file's SHA-256 matches {@code expectedHex} (case-insensitive). */
    public static boolean verify(@NonNull File apk, @NonNull String expectedHex) {
        try {
            String actual = sha256(apk);
            boolean ok = actual.equalsIgnoreCase(expectedHex.trim());
            if (!ok) {
                Log.w(TAG, "checksum mismatch: expected=" + expectedHex + " actual=" + actual);
            }
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "checksum failed", e);
            return false;
        }
    }

    /**
     * Launches the system installer for {@code apk}. Caller must have verified the checksum
     * and confirmed {@link #canInstall} first.
     */
    public static void install(@NonNull Context context, @NonNull File apk) {
        Uri apkUri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}