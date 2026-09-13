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
    private int pageCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferencesManager preferences = new PreferencesManager(this);
        shortcutsEnabled = preferences.isShortcutsPageEnabled();
        pageCount = HomePagerAdapter.pagesFor(shortcutsEnabled).size();

        pager = findViewById(R.id.home_pager);
        adapter = new HomePagerAdapter(this, shortcutsEnabled);
        pager.setAdapter(adapter);

        pageBars = new View[]{
                findViewById(R.id.page_bar_0),
                findViewById(R.id.page_bar_1),
                findViewById(R.id.page_bar_2)};
        // One bar per page on show: with the shortcuts page off, the third would be a dot
        // the user can never reach.
        for (int i = 0; i < pageBars.length; i++) {
            pageBars[i].setVisibility(i < pageCount ? View.VISIBLE : View.GONE);
        }

        // Open on the page chosen in settings, without animating in from page one.
        pager.setCurrentItem(
                HomePagerAdapter.positionOf(preferences.getHomePage(), shortcutsEnabled), false);
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
        new PreferencesManager(this).pruneGridFavorites();

        // Remove any APK left over from a previous (completed or cancelled) update.
        ApkDownloader.clearDownloads(this);

        // Silently check for a newer build on launch; prompts the user only if one exists.
        updateManager = new UpdateManager(this);
        updateManager.checkForUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings can add or remove a page while this activity sits in the background.
        // Reshaping the adapter beats recreating the activity: the two surviving pages keep
        // their state, and the user does not watch the launcher blink.
        boolean enabled = new PreferencesManager(this).isShortcutsPageEnabled();
        if (enabled != shortcutsEnabled) {
            applyCarouselShape(enabled);
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
    private void applyCarouselShape(boolean enabled) {
        shortcutsEnabled = enabled;
        pageCount = HomePagerAdapter.pagesFor(enabled).size();
        adapter.setShortcutsEnabled(enabled);
        for (int i = 0; i < pageBars.length; i++) {
            pageBars[i].setVisibility(i < pageCount ? View.VISIBLE : View.GONE);
        }
        // Removing a page can leave the pager on an index that no longer exists.
        pager.post(() -> {
            int position = Math.min(pager.getCurrentItem(), pageCount - 1);
            pager.setCurrentItem(position, false);
            updateIndicator(position);
        });
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
