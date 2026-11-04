package com.limelight.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.limelight.PcView;

/** Retains the core PIN/certificate flow inside an explicitly temporary TV management screen. */
public final class TvPairingActivity extends PcView {
    static final String HOST_UUID = "vibelight_paired_host_uuid";

    @Override public void setContentView(int layout) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.rgb(6,9,24));
        LinearLayout header = new LinearLayout(this);
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        header.setPadding(margin,margin/2,margin,margin/2);
        TextView title = new TextView(this);
        title.setText("Pair & manage PCs"); title.setTextSize(20); title.setTextColor(Color.rgb(226,232,240));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Button back = new Button(this);
        back.setText("Return to Vibelight TV"); back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        header.addView(back,new LinearLayout.LayoutParams(-2,-2));
        wrapper.addView(header,new LinearLayout.LayoutParams(-1,-2));
        View core = getLayoutInflater().inflate(layout,wrapper,false);
        wrapper.addView(core,new LinearLayout.LayoutParams(-1,0,1));
        super.setContentView(wrapper);
    }

    public void returnToDashboard(String uuid) {
        setResult(Activity.RESULT_OK,new Intent().putExtra(HOST_UUID,uuid));
        finish();
    }
}
