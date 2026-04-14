package com.coderscollective.mapsreceiver;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class HeadlessShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            if ("text/plain".equals(intent.getType())) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    // Pass text to background service
                    Intent serviceIntent = new Intent(this, FileProcessorService.class);
                    serviceIntent.putExtra("shared_text", sharedText);
                    startService(serviceIntent);
                }
            }
        }

        // Close immediately so the user never sees a UI
        finish();
    }
}
