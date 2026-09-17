package com.tommasov.mg4simplelauncher.diag;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tommasov.mg4simplelauncher.Dialogs;
import com.tommasov.mg4simplelauncher.R;

/**
 * Shows the diagnostics log on the head unit itself, since the car cannot be reached over
 * adb. Read-only apart from sending it to the author's probe and clearing it.
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private TextView content;
    private TextView sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        content = findViewById(R.id.diagnostics_content);
        sendButton = findViewById(R.id.diagnostics_send);
        findViewById(R.id.diagnostics_back_button).setOnClickListener(v -> finish());

        // Without a key this build has nowhere to send to, so the button is not offered
        // rather than offered and broken.
        sendButton.setVisibility(ProbeReport.isConfigured() ? View.VISIBLE : View.GONE);
        sendButton.setOnClickListener(v -> askThenSend());

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

    /**
     * Asks before sending, and asks for one sentence while it is at it.
     *
     * <p>Two things happen in this dialogue and both are needed. The log leaves the car for
     * somebody else's server, and it contains position fixes among other things, so it is
     * not something to send on a stray tap without saying so. And a log arriving on its own
     * is half a report: what the driver was doing when it went wrong is the half that makes
     * the other half readable, and nobody will write it down afterwards.
     */
    private void askThenSend() {
        Context scaled = Dialogs.scaled(this);

        TextView explanation = new TextView(scaled);
        explanation.setText(R.string.diagnostics_send_explain);
        explanation.setTextColor(getColor(R.color.text_secondary));
        explanation.setTextSize(getResources().getDimension(R.dimen.secondary_text_size)
                / getResources().getDisplayMetrics().scaledDensity);

        EditText note = new EditText(scaled);
        note.setHint(R.string.diagnostics_send_note_hint);
        note.setSingleLine(true);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        int pad = getResources().getDimensionPixelSize(R.dimen.card_gap);
        LinearLayout body = new LinearLayout(scaled);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, 0);
        body.addView(explanation);
        body.addView(note);

        Dialogs.builder(this)
                .setTitle(R.string.diagnostics_send_title)
                .setView(body)
                .setPositiveButton(R.string.diagnostics_send,
                        (d, which) -> send(note.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void send(String note) {
        sendButton.setEnabled(false);
        sendButton.setText(R.string.diagnostics_sending);
        ProbeReport.send(this, note, new ProbeReport.Callback() {
            @Override
            public void onSent(String reportName) {
                restoreButton();
                Dialogs.toast(DiagnosticsActivity.this, R.string.diagnostics_sent,
                        Toast.LENGTH_SHORT);
                DiagnosticsLog.log(DiagnosticsActivity.this, "Probe", "report sent as "
                        + reportName);
            }

            @Override
            public void onFailed(String reason) {
                restoreButton();
                // A dialogue rather than a toast: this one is worth reading, and the reason
                // is the only thing that says whether to try again or to fix something.
                Dialogs.builder(DiagnosticsActivity.this)
                        .setTitle(R.string.diagnostics_send_failed)
                        .setMessage(reason)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    private void restoreButton() {
        sendButton.setEnabled(true);
        sendButton.setText(R.string.diagnostics_send);
    }
}
