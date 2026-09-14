package com.tommasov.mg4simplelauncher;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Device, memory, storage and network, on a screen reached from the settings.
 *
 * <p>A screen rather than a card on the carousel: none of it is any use while driving, and
 * the tools page it used to share is now the two things that are — where the charging points
 * are, and what the launcher is set to do.
 */
public class TechnicalDetailsActivity extends AppCompatActivity {

    private TechnicalDetails details;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_technical_details);
        findViewById(R.id.technical_back_button).setOnClickListener(v -> finish());
        details = new TechnicalDetails(findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        details.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        details.stop();
    }
}
