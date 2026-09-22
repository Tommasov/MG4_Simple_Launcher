package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
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
 * would not survive having its text grown underneath it. Toasts are handled separately, and
 * for the opposite reason — see {@link #toast}.
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
     * The smallest a toast may be on this screen, in sp.
     *
     * <p>A floor, not a multiplier. The MG4's framework already sets its toasts to 42sp —
     * three times the Android default, because SAIC had the same problem we do and solved it
     * once for the whole head unit. Scaling that by another 1.4 gave the 59sp banner the
     * driver reported. So the rule is only ever to raise a toast that is too small, which on
     * the car means changing nothing at all and on a stock emulator means lifting the 14sp
     * default to something readable from the seat.
     */
    private static final float TOAST_MIN_SP = 28f;

    /**
     * A toast that is readable from the driving position, whatever platform it lands on.
     *
     * <p>Unlike the dialogs, this does not go through {@link #scaled}: the font scale is a
     * multiplier, and multiplying a size the platform has already enlarged compounds instead
     * of correcting. Setting the floor directly leaves the vendor's own choice alone.
     */
    public static void toast(@NonNull Context context, @StringRes int messageRes, int duration) {
        toast(context, context.getText(messageRes), duration);
    }

    /** As above, for a message that is not a resource. */
    public static void toast(@NonNull Context context, @NonNull CharSequence message,
                             int duration) {
        Toast toast = Toast.makeText(context, message, duration);
        raiseToFloor(context, toast);
        toast.show();
    }

    /**
     * Grows a toast's text if the platform's own size is below the floor, and otherwise lets
     * it be.
     *
     * <p>The view is only reachable up to Android 10; from 11 the system renders text toasts
     * itself and {@code getView} returns nothing. Nothing this launcher runs on is affected,
     * and on anything that is, the system's own size stands.
     */
    @SuppressWarnings("deprecation")
    private static void raiseToFloor(@NonNull Context context, @NonNull Toast toast) {
        View view = toast.getView();
        View message = view == null ? null : view.findViewById(android.R.id.message);
        if (!(message instanceof TextView)) {
            return;
        }
        TextView text = (TextView) message;
        // applyDimension carries the driver's own font setting, so raising the floor never
        // undoes an enlargement they asked for themselves.
        float floor = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TOAST_MIN_SP,
                context.getResources().getDisplayMetrics());
        if (text.getTextSize() < floor) {
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, floor);
        }
    }

    /**
     * Wraps a dialogue's body so it can scroll.
     *
     * <p>Needed wherever the contents are as long as the data makes them — a list of the
     * charging networks around the car is two entries in one place and fifteen in another —
     * because a dialogue taller than 720px simply loses its buttons off the bottom of this
     * screen.
     */
    @NonNull
    public static android.view.View scroll(@NonNull Context context,
                                           @NonNull android.view.View body) {
        android.widget.ScrollView scroller = new android.widget.ScrollView(context);
        scroller.addView(body);
        return scroller;
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
