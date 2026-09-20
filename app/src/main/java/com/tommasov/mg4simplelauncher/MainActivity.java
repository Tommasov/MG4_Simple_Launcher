package com.tommasov.mg4simplelauncher;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.tommasov.mg4simplelauncher.update.ApkDownloader;
import com.tommasov.mg4simplelauncher.update.UpdateManager;

/**
 * Hosts the three-page home carousel ({@link HomePagerAdapter}) and the bottom pagination
 * bars. Page 1 is the launcher home, page 2 the shortcut grid, page 3 the useful-info
 * screen; the shortcut grid can be switched off in settings, leaving two pages.
 */
public class MainActivity extends AppCompatActivity {

    private View[] pageBars;
    private UpdateManager updateManager;
    private ViewPager2 pager;
    private HomePagerAdapter adapter;
    /** Carousel shape the current views were built for, to spot a settings change. */
    private boolean shortcutsEnabled;
    private boolean chargingEnabled;
    private int pageCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferencesManager preferences = new PreferencesManager(this);
        // Before the first read: the carousel is shaped by these preferences, and the
        // migration decides what one of them says on an installation that predates it.
        preferences.migrate();
        shortcutsEnabled = preferences.isShortcutsPageEnabled();
        chargingEnabled = preferences.isChargingPageEnabled();
        pageCount = HomePagerAdapter.pagesFor(shortcutsEnabled, chargingEnabled).size();

        pager = findViewById(R.id.home_pager);
        adapter = new HomePagerAdapter(this, shortcutsEnabled, chargingEnabled);
        pager.setAdapter(adapter);

        pageBars = new View[]{
                findViewById(R.id.page_bar_0),
                findViewById(R.id.page_bar_1),
                findViewById(R.id.page_bar_2)};
        // One bar per page on show: with the shortcuts page off, the third would be a dot
        // the user can never reach.
        showIndicator();

        // Open on the page chosen in settings, without animating in from page one.
        pager.setCurrentItem(
                HomePagerAdapter.positionOf(preferences.getHomePage(), shortcutsEnabled,
                        chargingEnabled), false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }
        });
        // Post so the indicator reads the page ViewPager2 restores after a layout pass
        // (state restore doesn't reliably fire onPageSelected for the initial position).
        pager.post(() -> updateIndicator(pager.getCurrentItem()));

        // Forget shortcut slots that no longer have a tile after the 1.5 grid was resized.
        preferences.pruneGridFavorites();

        // Remove any APK left over from a previous (completed or cancelled) update.
        ApkDownloader.clearDownloads(this);

        // Silently check for a newer build on launch; prompts the user only if one exists.
        // The manager is built either way — it owns the teardown in onDestroy — but the check
        // only runs when the driver has left it on.
        updateManager = new UpdateManager(this);
        if (new PreferencesManager(this).isUpdateCheckOnLaunchEnabled()) {
            updateManager.checkForUpdates(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings can add or remove a page while this activity sits in the background.
        // Reshaping the adapter beats recreating the activity: the two surviving pages keep
        // their state, and the user does not watch the launcher blink.
        PreferencesManager current = new PreferencesManager(this);
        boolean shortcuts = current.isShortcutsPageEnabled();
        boolean charging = current.isChargingPageEnabled();
        if (shortcuts != shortcutsEnabled || charging != chargingEnabled) {
            applyCarouselShape(shortcuts, charging);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tear down any in-flight download/dialog so it can't leak the window or a receiver.
        if (updateManager != null) {
            updateManager.cancel();
        }
    }

    /** Applies a page added or removed in settings to the pager and the indicator. */
    private void applyCarouselShape(boolean shortcuts, boolean charging) {
        shortcutsEnabled = shortcuts;
        chargingEnabled = charging;
        pageCount = HomePagerAdapter.pagesFor(shortcuts, charging).size();
        adapter.setPages(shortcuts, charging);
        showIndicator();
        // Removing a page can leave the pager on an index that no longer exists.
        pager.post(() -> {
            int position = Math.min(pager.getCurrentItem(), pageCount - 1);
            pager.setCurrentItem(position, false);
            updateIndicator(position);
        });
    }

    /**
     * One bar per page on show, and none at all when there is only the home left: a single
     * dot under a screen you cannot swipe away from says nothing, and looks like a carousel
     * that has broken rather than one the driver emptied on purpose.
     */
    private void showIndicator() {
        boolean any = pageCount > 1;
        for (int i = 0; i < pageBars.length; i++) {
            pageBars[i].setVisibility(any && i < pageCount ? View.VISIBLE : View.GONE);
        }
    }

    /** Highlights the bar of the current page and shrinks the others (SAIC-style pagination). */
    private void updateIndicator(int position) {
        for (int i = 0; i < pageCount; i++) {
            boolean active = i == position;
            View bar = pageBars[i];
            bar.getLayoutParams().width = getResources().getDimensionPixelSize(active
                    ? R.dimen.page_indicator_active_width
                    : R.dimen.page_indicator_inactive_width);
            bar.setBackgroundResource(active
                    ? R.drawable.page_bar_active
                    : R.drawable.page_bar_inactive);
            bar.requestLayout();
        }
    }
}
