package com.walkman.x10mini;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class JellyfinSettingsActivity extends Activity {

    private EditText mUrlEdit;
    private EditText mUserEdit;
    private EditText mPassEdit;
    private Button mConnectBtn;
    private TextView mStatus;
    private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jellyfin_settings);

        mUrlEdit = (EditText) findViewById(R.id.jf_server_url);
        mUserEdit = (EditText) findViewById(R.id.jf_username);
        mPassEdit = (EditText) findViewById(R.id.jf_password);
        mConnectBtn = (Button) findViewById(R.id.jf_connect);
        mStatus = (TextView) findViewById(R.id.jf_status);

        SharedPreferences sp = getSharedPreferences("jellyfin", MODE_PRIVATE);
        String savedUrl = sp.getString("server_url", "");
        String savedUser = sp.getString("username", "");
        if (savedUrl.length() > 0) mUrlEdit.setText(savedUrl);
        if (savedUser.length() > 0) mUserEdit.setText(savedUser);

        if (sp.getString("access_token", null) != null) {
            mStatus.setText("Connected");
            mStatus.setTextColor(getResources().getColor(R.color.walkman_blue));
            mConnectBtn.setText("Reconnect");
        }

        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connect();
            }
        });
    }

    private void connect() {
        final String url = mUrlEdit.getText().toString().trim();
        final String user = mUserEdit.getText().toString().trim();
        final String pass = mPassEdit.getText().toString();

        if (url.length() == 0 || user.length() == 0) {
            mStatus.setText("Enter server URL and username");
            mStatus.setTextColor(getResources().getColor(R.color.text_secondary));
            return;
        }

        mConnectBtn.setEnabled(false);
        mStatus.setText("Connecting...");
        mStatus.setTextColor(getResources().getColor(R.color.text_secondary));

        new Thread(new Runnable() {
            public void run() {
                final JellyfinClient client = new JellyfinClient();
                final boolean ok = client.authenticate(url, user, pass);
                mHandler.post(new Runnable() {
                    public void run() {
                        mConnectBtn.setEnabled(true);
                        if (ok) {
                            client.saveToPrefs(JellyfinSettingsActivity.this);
                            SharedPreferences.Editor ed = getSharedPreferences("jellyfin", MODE_PRIVATE).edit();
                            ed.putString("username", user);
                            ed.commit();
                            mStatus.setText("Connected successfully");
                            mStatus.setTextColor(getResources().getColor(R.color.walkman_blue));
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            mStatus.setText("Connection failed. Check URL and credentials.");
                            mStatus.setTextColor(0xFFFF6666);
                        }
                    }
                });
            }
        }).start();
    }
}
