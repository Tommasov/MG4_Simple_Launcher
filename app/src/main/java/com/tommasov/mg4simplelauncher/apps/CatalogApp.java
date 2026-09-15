package com.tommasov.mg4simplelauncher.apps;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One app offered by the catalogue: what it is, which build is published, and where the APK
 * lives.
 *
 * <p>Everything the launcher needs to decide whether to offer an install or an update is in
 * here rather than derived from the file: a release tag is a name, not an ordering, and the
 * name inside an APK has been known to disagree with the tag it was published under.
 */
public class CatalogApp {

    public final String packageName;
    public final String name;
    /**
     * Who made it, when that is not the launcher's author. Shown in the list so nobody
     * installs somebody else's work believing it comes from here, and so that praise and
     * complaints reach the right person. Empty for the author's own apps.
     */
    public final String author;
    /** Already resolved to the reader's language; see {@link #describe}. */
    public final String description;
    public final long versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String sha256;
    /** Download size in bytes, or 0 when the catalogue does not say. */
    public final long sizeBytes;

    private CatalogApp(String packageName, String name, String author, String description,
                       long versionCode, String versionName, String apkUrl, String sha256,
                       long sizeBytes) {
        this.packageName = packageName;
        this.name = name;
        this.author = author;
        this.description = description;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    @NonNull
    static CatalogApp fromJson(@NonNull JSONObject json, @NonNull String baseUrl)
            throws JSONException {
        String packageName = json.getString("package");
        String apkUrl = json.getString("apkUrl");
        String sha256 = json.getString("sha256");
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(apkUrl)
                || TextUtils.isEmpty(sha256)) {
            throw new JSONException("package, apkUrl and sha256 are required");
        }
        return new CatalogApp(
                packageName,
                json.optString("name", packageName),
                json.optString("author", ""),
                describe(json.opt("description")),
                json.getLong("versionCode"),
                json.optString("versionName", ""),
                resolveUrl(baseUrl, apkUrl),
                sha256.trim().toLowerCase(Locale.US),
                json.optLong("sizeBytes", 0));
    }

    /**
     * Picks the description in the reader's language.
     *
     * <p>The field is either a plain string or an object keyed by language. Italian when the
     * launcher is running in Italian and the catalogue has it, English otherwise — English
     * being the one language every entry is expected to carry, and a description in a
     * language you cannot read is worse than a short one you can.
     */
    @NonNull
    private static String describe(@Nullable Object description) {
        if (description instanceof String) {
            return (String) description;
        }
        if (description instanceof JSONObject) {
            JSONObject byLanguage = (JSONObject) description;
            String language = Locale.getDefault().getLanguage();
            String mine = byLanguage.optString(language, "");
            if (!mine.isEmpty()) {
                return mine;
            }
            return byLanguage.optString("en", "");
        }
        return "";
    }

    /** Resolves a relative apkUrl against the catalogue's own address. */
    @NonNull
    private static String resolveUrl(@NonNull String baseUrl, @NonNull String apkUrl) {
        if (Uri.parse(apkUrl).isAbsolute()) {
            return apkUrl;
        }
        int lastSlash = baseUrl.lastIndexOf('/');
        String directory = lastSlash >= 0 ? baseUrl.substring(0, lastSlash + 1) : baseUrl + "/";
        return directory + apkUrl;
    }
}
