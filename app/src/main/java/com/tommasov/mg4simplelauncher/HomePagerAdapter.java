package com.tommasov.mg4simplelauncher;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal carousel: the home, the shortcut grid and the system-info screen.
 *
 * <p>The shortcuts page can be switched off in settings, so pages are identified by a
 * stable <em>kind</em> rather than by position: positions shift when a page disappears,
 * and a stored "open on page 2" would then point at the wrong screen.
 */
public class HomePagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_HOME = 0;
    public static final int PAGE_SHORTCUTS = 1;
    public static final int PAGE_CHARGING = 2;

    private final List<Integer> pages = new ArrayList<>(3);

    public HomePagerAdapter(@NonNull FragmentActivity activity, boolean shortcutsEnabled,
                            boolean chargingEnabled) {
        super(activity);
        this.pages.addAll(pagesFor(shortcutsEnabled, chargingEnabled));
    }

    /** Adds or removes an optional page in place, keeping the surviving ones alive. */
    public void setPages(boolean shortcutsEnabled, boolean chargingEnabled) {
        List<Integer> updated = pagesFor(shortcutsEnabled, chargingEnabled);
        if (updated.equals(pages)) {
            return;
        }
        pages.clear();
        pages.addAll(updated);
        notifyDataSetChanged();
    }

    /**
     * The page kinds on show, in carousel order.
     *
     * <p>Both of the outer two can be switched off, and the home cannot: it is the launcher.
     * Someone who lives in their apps keeps the shortcuts grid and drops the chargers,
     * someone who bought the car for the chargers does the opposite, and most people keep
     * what they are given.
     */
    @NonNull
    public static List<Integer> pagesFor(boolean shortcutsEnabled, boolean chargingEnabled) {
        List<Integer> kinds = new ArrayList<>(3);
        kinds.add(PAGE_HOME);
        if (shortcutsEnabled) {
            kinds.add(PAGE_SHORTCUTS);
        }
        if (chargingEnabled) {
            kinds.add(PAGE_CHARGING);
        }
        return kinds;
    }

    /** Carousel position showing {@code kind}, or 0 when that page is not on show. */
    public static int positionOf(int kind, boolean shortcutsEnabled, boolean chargingEnabled) {
        int position = pagesFor(shortcutsEnabled, chargingEnabled).indexOf(kind);
        return position < 0 ? 0 : position;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (pages.get(position)) {
            case PAGE_SHORTCUTS:
                return new FavoritesGridFragment();
            case PAGE_CHARGING:
                return new ChargingFragment();
            default:
                return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    /**
     * Identify pages by kind, not by position. FragmentStateAdapter caches fragments against
     * these ids, and the default implementation uses the position: drop the shortcuts page
     * and position 1 stops meaning "shortcuts" and starts meaning "system info", so the
     * cached shortcuts fragment gets handed back for the system-info slot and the carousel
     * shows duplicated or mismatched pages until the process restarts.
     */
    @Override
    public long getItemId(int position) {
        return pages.get(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        return pages.contains((int) itemId);
    }
}
