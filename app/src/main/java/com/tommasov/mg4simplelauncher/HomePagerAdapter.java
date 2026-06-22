package com.tommasov.mg4simplelauncher;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/** Two-page horizontal carousel: the home (page 0) and the system-info screen (page 1). */
public class HomePagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_COUNT = 2;

    public HomePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return new SystemInfoFragment();
        }
        return new HomeFragment();
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
