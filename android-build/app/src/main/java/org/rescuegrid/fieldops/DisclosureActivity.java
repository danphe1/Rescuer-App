package org.rescuegrid.fieldops;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

public class DisclosureActivity extends Activity {
    private static final String PREFS = "rescuegrid_policy";
    private static final String KEY_ACCEPTED = "sensitive_data_disclosure_accepted_v1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)) {
            openResponder();
            return;
        }
        showDisclosure();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        v.setLineSpacing(0f, 1.15f);
        return v;
    }

    private void showDisclosure() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(4, 29, 52));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(38), dp(24), dp(28));
        scroll.addView(root);

        TextView brand = text("RescueGrid Responder", 26, Color.WHITE);
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        brand.setPadding(0, 0, 0, dp(8));
        root.addView(brand);

        TextView heading = text("Your data and device permissions", 22, Color.rgb(49, 230, 160));
        heading.setPadding(0, dp(10), 0, dp(14));
        root.addView(heading);

        TextView body = text(
            "RescueGrid uses precise location while you use the responder app to show your operational position to authorized command personnel, record mission check-ins, and attach location to safety or SOS events.\n\n" +
            "If connectivity is lost, supported GPS points may be stored on your device and synchronized when the connection returns. Location is not collected for advertising. This Android release does not request background-location permission.\n\n" +
            "Camera access is used only when you choose to capture or upload a profile or mission image.\n\n" +
            "Your operational information may be visible to authorized personnel in your participating organization for mission coordination and responder safety.\n\n" +
            "You can decline by exiting the app. If you continue, Android or the web app may separately ask for the permissions needed by a feature.",
            16,
            Color.rgb(214, 229, 239)
        );
        root.addView(body);

        Button privacy = new Button(this);
        privacy.setText("View Privacy Policy");
        privacy.setAllCaps(false);
        privacy.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_url)));
            startActivity(i);
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(26), 0, dp(10));
        root.addView(privacy, bp);

        Button accept = new Button(this);
        accept.setText("Continue to RescueGrid");
        accept.setAllCaps(false);
        accept.setTextColor(Color.WHITE);
        accept.setBackgroundColor(Color.rgb(8, 120, 209));
        accept.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACCEPTED, true).apply();
            openResponder();
        });
        root.addView(accept, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        Button exit = new Button(this);
        exit.setText("Exit");
        exit.setAllCaps(false);
        exit.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, dp(8), 0, 0);
        root.addView(exit, ep);

        TextView note = text("RescueGrid is an operational coordination tool. Follow your organization’s emergency procedures and primary communication methods.", 12, Color.rgb(160, 183, 199));
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        note.setPadding(0, dp(22), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void openResponder() {
        Intent intent = new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
    }
}
