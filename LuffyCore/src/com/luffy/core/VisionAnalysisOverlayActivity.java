package com.luffy.core;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class VisionAnalysisOverlayActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure floating window style
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 80;
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        getWindow().setAttributes(params);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF141414);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("LUFFY VISION");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView status = new TextView(this);
        status.setText("\nScreen snapshot captured.\nAnalyzing visual content...\n\nTap to dismiss.");
        status.setTextColor(0xFFAAAAAA);
        status.setTextSize(14);
        layout.addView(status);

        layout.setOnClickListener(v -> finish());
        setContentView(layout);
    }
}
