package com.tommasov.mg4simplelauncher;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Three-page horizontal carousel: the home (page 0), the shortcut grid (page 1) and
 * the system-info screen (page 2).
 */
public class HomePagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_COUNT = 3;

    public HomePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return new FavoritesGridFragment();
            case 2:
                return new SystemInfoFragment();
            default:
                return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
