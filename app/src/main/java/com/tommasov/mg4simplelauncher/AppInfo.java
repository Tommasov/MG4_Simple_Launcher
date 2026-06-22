package com.tommasov.mg4simplelauncher;

import android.graphics.drawable.Drawable;

/** A launchable app shown in the drawer grid. */
public class AppInfo {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public final boolean system;

    public AppInfo(String label, String packageName, Drawable icon, boolean system) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.system = system;
    }
}