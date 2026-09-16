package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.res.Configuration;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

/**
 * Builds the launcher's dialogs at a size that can be read from the driver's seat.
 *
 * <p>Android's dialogs are sized for a phone held at arm's length. This screen is a metre
 * away, behind a steering wheel, and often read in the seconds before the lights change: at
 * the stock size the beta-channel warning and the install-permission explanation are legible
 * only if you lean in, which is the one thing a driver should not do.
 *
 * <p>The whole dialog is scaled through a {@link Configuration} rather than restyled piece by
 * piece, because one of the pieces cannot be restyled at all: AppCompat writes the message's
 * appearance into its own layout ({@code TextAppearance.AppCompat.Subhead}) instead of
 * reading it from a theme attribute, so a theme can enlarge the title, the list items and the
 * buttons but never the paragraph in the middle — the part that most needs it. A font scale
 * reaches all four, and reaches anything AppCompat adds later without being told about it.
 *
 * <p>Only dialogs are scaled. The rest of the launcher is laid out by hand for this screen and
 * would not survive having its text grown underneath it.
 */
public final class Dialogs {

    /**
     * How much larger than the system's own setting. Tuned by eye from the driving position;
     * it is deliberately one number, in one place, because that is how it will be retuned.
     */
    private static final float FONT_SCALE = 1.4f;

    private Dialogs() {
    }

    /**
     * A toast at the same enlarged size as the dialogs.
     *
     * <p>A toast built with the activity keeps the system's own text size, which on this
     * screen is a line of grey you cannot read before it fades — and a message nobody can
     * read in the seconds it lasts might as well not be shown.
     */
    public static void toast(@NonNull Context context, @StringRes int messageRes, int duration) {
        Toast.makeText(scaled(context), messageRes, duration).show();
    }

    /** As above, for a message that is not a resource. */
    public static void toast(@NonNull Context context, @NonNull CharSequence message,
                             int duration) {
        Toast.makeText(scaled(context), message, duration).show();
    }

    /** An {@link AlertDialog.Builder} whose text is sized for the car. */
    @NonNull
    public static AlertDialog.Builder builder(@NonNull Context context) {
        return new AlertDialog.Builder(scaled(context));
    }

    /**
     * The context to build a dialog's own views with, for the two dialogs that assemble their
     * contents by hand. Views made with the activity would keep the unscaled text while the
     * title and buttons around them grew.
     *
     * <p>The activity stays underneath: a dialog needs its window token, and a context made
     * with {@code createConfigurationContext} has none — it throws
     * {@code BadTokenException: token null is not valid} the moment it is shown. Overriding
     * the configuration on a wrapper keeps the activity and changes only what is asked for:
     * the override carries the font scale alone, and every other field is left untouched.
     *
     * <p>Multiplies whatever the system is set to rather than replacing it: a driver who has
     * already enlarged the head unit's font is asking for larger text, not for ours.
     */
    @NonNull
    public static Context scaled(@NonNull Context context) {
        Configuration override = new Configuration();
        override.fontScale = context.getResources().getConfiguration().fontScale * FONT_SCALE;
        ContextThemeWrapper wrapper =
                new ContextThemeWrapper(context, R.style.Theme_MG4SimpleLauncher);
        // Must happen before anything reads resources from the wrapper.
        wrapper.applyOverrideConfiguration(override);
        return wrapper;
    }
}
