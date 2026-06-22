package com.tommasov.mg4simplelauncher;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.tommasov.mg4simplelauncher.update.ApkDownloader;
import com.tommasov.mg4simplelauncher.update.UpdateManager;

/**
 * Hosts the two-page home carousel ({@link HomePagerAdapter}) and the bottom pagination
 * bars. Page 1 is the launcher home, page 2 the useful-info screen.
 */
public class MainActivity extends AppCompatActivity {

    private View[] pageBars;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager2 pager = findViewById(R.id.home_pager);
        pager.setAdapter(new HomePagerAdapter(this));

        pageBars = new View[]{
                findViewById(R.id.page_bar_0),
                findViewById(R.id.page_bar_1)};
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }
        });
        // Post so the indicator reads the page ViewPager2 restores after a layout pass
        // (state restore doesn't reliably fire onPageSelected for the initial position).
        pager.post(() -> updateIndicator(pager.getCurrentItem()));

        // Remove any APK left over from a previous (completed or cancelled) update.
        ApkDownloader.clearDownloads(this);

        // Silently check for a newer build on launch; prompts the user only if one exists.
        updateManager = new UpdateManager(this);
        updateManager.checkForUpdates(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tear down any in-flight download/dialog so it can't leak the window or a receiver.
        if (updateManager != null) {
            updateManager.cancel();
        }
    }

    /** Highlights the bar of the current page and shrinks the others (SAIC-style pagination). */
    private void updateIndicator(int position) {
        for (int i = 0; i < pageBars.length; i++) {
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
