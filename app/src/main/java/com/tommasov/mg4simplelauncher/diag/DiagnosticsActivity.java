package com.tommasov.mg4simplelauncher.diag;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tommasov.mg4simplelauncher.Dialogs;
import com.tommasov.mg4simplelauncher.R;

/**
 * Shows the diagnostics log on the head unit itself, since the car cannot be reached over
 * adb. Read-only apart from copying it out and clearing it.
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private TextView content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        content = findViewById(R.id.diagnostics_content);
        findViewById(R.id.diagnostics_back_button).setOnClickListener(v -> finish());

        findViewById(R.id.diagnostics_copy).setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(
                        ClipData.newPlainText("diagnostics", content.getText()));
                Dialogs.toast(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT);
            }
        });

        findViewById(R.id.diagnostics_clear).setOnClickListener(v -> {
            DiagnosticsLog.clear(this);
            content.setText(R.string.diagnostics_empty);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String log = DiagnosticsLog.read(this);
        content.setText(log.isEmpty() ? getString(R.string.diagnostics_empty) : log);
    }
}
