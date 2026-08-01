package com.walkman.x10mini;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class JellyfinAudioActivity extends Activity {

    private Spinner mCodecSpinner;
    private Spinner mBitrateSpinner;
    private Spinner mSampleRateSpinner;
    private View mBitrateRow;
    private View mSampleRateRow;
    private TextView mStatusText;

    private static final String[] CODEC_LABELS = {
            "Transcode to MP3 (Recommended)",
            "Transcode to AAC",
            "Transcode to OGG",
            "Transcode to WAV",
            "Direct (no transcoding)"
    };
    private static final int[] CODEC_VALUES = {
            JellyfinClient.CODEC_MP3,
            JellyfinClient.CODEC_AAC,
            JellyfinClient.CODEC_OGG,
            JellyfinClient.CODEC_WAV,
            JellyfinClient.CODEC_DIRECT
    };

    private static final String[] BITRATE_LABELS = {
            "64 kbps", "96 kbps", "128 kbps",
            "192 kbps", "256 kbps", "320 kbps"
    };
    private static final int[] BITRATE_VALUES = {
            64, 96, 128, 192, 256, 320
    };

    private static final String[] SAMPLERATE_LABELS = {
            "Original", "22050 Hz", "44100 Hz", "48000 Hz"
    };
    private static final int[] SAMPLERATE_VALUES = {
            0, 22050, 44100, 48000
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jellyfin_audio);

        mCodecSpinner = (Spinner) findViewById(R.id.jf_codec);
        mBitrateSpinner = (Spinner) findViewById(R.id.jf_bitrate);
        mSampleRateSpinner = (Spinner) findViewById(R.id.jf_samplerate);
        mBitrateRow = findViewById(R.id.jf_bitrate_row);
        mSampleRateRow = findViewById(R.id.jf_samplerate_row);
        mStatusText = (TextView) findViewById(R.id.jf_audio_status);

        ArrayAdapter<String> codecAdapter = new ArrayAdapter<String>(
                this, R.layout.spinner_item, CODEC_LABELS);
        codecAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mCodecSpinner.setAdapter(codecAdapter);

        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<String>(
                this, R.layout.spinner_item, BITRATE_LABELS);
        bitrateAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mBitrateSpinner.setAdapter(bitrateAdapter);

        ArrayAdapter<String> srAdapter = new ArrayAdapter<String>(
                this, R.layout.spinner_item, SAMPLERATE_LABELS);
        srAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mSampleRateSpinner.setAdapter(srAdapter);

        SharedPreferences sp = getSharedPreferences("jellyfin", MODE_PRIVATE);
        selectValue(mCodecSpinner, CODEC_VALUES, sp.getInt("codec", JellyfinClient.CODEC_MP3));
        selectValue(mBitrateSpinner, BITRATE_VALUES, sp.getInt("bitrate", 192));
        selectValue(mSampleRateSpinner, SAMPLERATE_VALUES, sp.getInt("samplerate", 0));

        mCodecSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateVisibility(pos);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        updateVisibility(mCodecSpinner.getSelectedItemPosition());

        mStatusText.setText("Changes apply to next track played");
    }

    private void updateVisibility(int codecIdx) {
        if (codecIdx < 0 || codecIdx >= CODEC_VALUES.length) return;
        int codec = CODEC_VALUES[codecIdx];
        boolean isDirect = codec == JellyfinClient.CODEC_DIRECT;
        boolean isWav = codec == JellyfinClient.CODEC_WAV;
        mBitrateRow.setVisibility(isDirect || isWav ? View.GONE : View.VISIBLE);
        mSampleRateRow.setVisibility(isDirect ? View.GONE : View.VISIBLE);
    }

    private void selectValue(Spinner spinner, int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int getSelected(Spinner spinner, int[] values, int def) {
        int idx = spinner.getSelectedItemPosition();
        return (idx >= 0 && idx < values.length) ? values[idx] : def;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor ed = getSharedPreferences("jellyfin", MODE_PRIVATE).edit();
        ed.putInt("codec", getSelected(mCodecSpinner, CODEC_VALUES, JellyfinClient.CODEC_MP3));
        ed.putInt("bitrate", getSelected(mBitrateSpinner, BITRATE_VALUES, 192));
        ed.putInt("samplerate", getSelected(mSampleRateSpinner, SAMPLERATE_VALUES, 0));
        ed.commit();
    }
}
