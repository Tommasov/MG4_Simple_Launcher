package com.tommasov.mg4simplelauncher.apps;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tommasov.mg4simplelauncher.R;

import java.util.ArrayList;
import java.util.List;

/** Binds the catalogue into the downloads list, one card per app. */
public class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.AppViewHolder> {

    /** What the launcher can do about an app, given what is on the vehicle already. */
    enum State {
        INSTALL, UPDATE, UP_TO_DATE
    }

    public interface Listener {
        /** The row's button was pressed: fetch and install this build. */
        void onAction(@NonNull CatalogApp app);
    }

    private final List<CatalogApp> apps = new ArrayList<>();
    private final Listener listener;

    public CatalogAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<CatalogApp> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_catalog_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        CatalogApp app = apps.get(position);
        Context context = holder.itemView.getContext();
        holder.name.setText(app.author.isEmpty()
                ? app.name
                : context.getString(R.string.downloads_by, app.name, app.author));
        holder.description.setText(app.description);
        holder.description.setVisibility(app.description.isEmpty() ? View.GONE : View.VISIBLE);

        long installed = installedVersion(context, app.packageName);
        State state = stateOf(installed, app.versionCode);
        holder.state.setText(describeState(context, app, installed, state));
        holder.action.setText(actionLabel(state));
        // An app already up to date has nothing to press: the button says so and stays quiet
        // rather than downloading a build the car is already running.
        boolean actionable = state != State.UP_TO_DATE;
        holder.action.setEnabled(actionable);
        holder.action.setAlpha(actionable ? 1f : 0.4f);
        holder.action.setOnClickListener(actionable ? v -> listener.onAction(app) : null);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    /** The installed build's version code, or -1 when the app is not on the vehicle. */
    private static long installedVersion(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            //noinspection deprecation
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    @NonNull
    private static State stateOf(long installed, long published) {
        if (installed < 0) {
            return State.INSTALL;
        }
        // Only a higher published build is an update. Equal means nothing to do, and lower
        // means the car is running something newer than the catalogue knows — a beta tester,
        // most likely, who must not be offered a downgrade Android would refuse anyway.
        return published > installed ? State.UPDATE : State.UP_TO_DATE;
    }

    @NonNull
    private static String describeState(@NonNull Context context, @NonNull CatalogApp app,
                                        long installed, @NonNull State state) {
        String published = app.versionName.isEmpty()
                ? String.valueOf(app.versionCode)
                : app.versionName;
        if (state == State.INSTALL) {
            return context.getString(R.string.downloads_available, published);
        }
        if (state == State.UPDATE) {
            return context.getString(R.string.downloads_update_from, published);
        }
        return context.getString(R.string.downloads_current, published);
    }

    private static int actionLabel(@NonNull State state) {
        switch (state) {
            case INSTALL:
                return R.string.downloads_install;
            case UPDATE:
                return R.string.update_action_install;
            default:
                return R.string.downloads_installed;
        }
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final TextView state;
        final TextView action;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.catalog_name);
            description = itemView.findViewById(R.id.catalog_description);
            state = itemView.findViewById(R.id.catalog_state);
            action = itemView.findViewById(R.id.catalog_action);
        }
    }
}
