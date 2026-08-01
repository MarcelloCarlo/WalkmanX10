package com.walkman.x10mini;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;

public class EditInfoActivity extends Activity {
    private long mTrackId;
    private long mAlbumId = -1;
    private String mFilePath;
    private String mReleaseId;
    private EditText mEditTitle;
    private EditText mEditArtist;
    private EditText mEditAlbum;
    private EditText mEditYear;
    private ImageView mAlbumArt;
    private Button mBtnSave;
    private Button mBtnLookup;
    private Button mBtnCancel;
    private TextView mStatusText;
    private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_info);

        mTrackId = getIntent().getLongExtra("track_id", -1);

        mAlbumArt = (ImageView) findViewById(R.id.edit_album_art);
        mEditTitle = (EditText) findViewById(R.id.edit_title);
        mEditArtist = (EditText) findViewById(R.id.edit_artist);
        mEditAlbum = (EditText) findViewById(R.id.edit_album);
        mEditYear = (EditText) findViewById(R.id.edit_year);
        mBtnSave = (Button) findViewById(R.id.btn_save);
        mBtnLookup = (Button) findViewById(R.id.btn_lookup);
        mBtnCancel = (Button) findViewById(R.id.btn_cancel);
        mStatusText = (TextView) findViewById(R.id.status_text);

        loadTrackInfo();

        mBtnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveTrackInfo(); }
        });

        mBtnLookup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { lookupInfo(); }
        });

        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        if (getIntent().getBooleanExtra("auto_lookup", false)) {
            lookupInfo();
        }
    }

    private void loadTrackInfo() {
        if (mTrackId < 0) return;
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM_ID
                },
                MediaStore.Audio.Media._ID + "=?",
                new String[]{String.valueOf(mTrackId)},
                null);
        if (c != null) {
            if (c.moveToFirst()) {
                mEditTitle.setText(c.getString(0));
                mEditArtist.setText(c.getString(1));
                mEditAlbum.setText(c.getString(2));
                String year = c.getString(3);
                if (year != null && !year.equals("0")) {
                    mEditYear.setText(year);
                }
                mFilePath = c.getString(4);
                mAlbumId = c.getLong(5);
            }
            c.close();
        }
        loadAlbumArt();
    }

    private void loadAlbumArt() {
        if (mAlbumId < 0) return;
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), mAlbumId);
            InputStream in = getContentResolver().openInputStream(artUri);
            if (in != null) {
                Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) {
                    mAlbumArt.setImageBitmap(bm);
                    return;
                }
            }
        } catch (Exception e) {
        }
        File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/" + mAlbumId + ".jpg");
        if (artFile.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(artFile.getAbsolutePath());
            if (bm != null) {
                mAlbumArt.setImageBitmap(bm);
                return;
            }
        }
        try {
            Cursor ac = getContentResolver().query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID + "=?",
                    new String[]{String.valueOf(mAlbumId)},
                    null);
            if (ac != null) {
                if (ac.moveToFirst()) {
                    String artPath = ac.getString(0);
                    if (artPath != null) {
                        Bitmap bm = BitmapFactory.decodeFile(artPath);
                        if (bm != null) {
                            ac.close();
                            mAlbumArt.setImageBitmap(bm);
                            return;
                        }
                    }
                }
                ac.close();
            }
        } catch (Exception e) {
        }
        mAlbumArt.setImageResource(R.drawable.musicplayer_default_album);
    }

    private void saveTrackInfo() {
        final String title = mEditTitle.getText().toString().trim();
        final String artist = mEditArtist.getText().toString().trim();
        final String album = mEditAlbum.getText().toString().trim();
        final String year = mEditYear.getText().toString().trim();

        mStatusText.setText("Saving...");
        mBtnSave.setEnabled(false);

        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.TITLE, title);
                values.put(MediaStore.Audio.Media.ARTIST, artist);
                values.put(MediaStore.Audio.Media.ALBUM, album);
                if (year.length() > 0) {
                    try {
                        values.put(MediaStore.Audio.Media.YEAR, Integer.parseInt(year));
                    } catch (NumberFormatException e) {
                    }
                }

                final int rows = getContentResolver().update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Media._ID + "=?",
                        new String[]{String.valueOf(mTrackId)});

                if (mFilePath != null && mFilePath.length() > 0) {
                    MetadataUtils.writeId3v1Tag(mFilePath, title, artist, album, year);
                    sendBroadcast(new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(new File(mFilePath))));
                }

                mHandler.post(new Runnable() {
                    public void run() {
                        mBtnSave.setEnabled(true);
                        if (rows > 0) {
                            Toast.makeText(EditInfoActivity.this,
                                    "Track info saved", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            mStatusText.setText("Failed to save");
                        }
                    }
                });
            }
        }).start();
    }

    private void lookupInfo() {
        final String title = mEditTitle.getText().toString().trim();
        final String artist = mEditArtist.getText().toString().trim();

        if (title.length() == 0) {
            mStatusText.setText("Title is required for lookup");
            return;
        }

        mStatusText.setText("Looking up...");
        mBtnLookup.setEnabled(false);

        new Thread(new Runnable() {
            public void run() {
                final MetadataUtils.LookupResult result =
                        MetadataUtils.lookupMusicBrainz(title, artist);

                if (!result.found) {
                    postStatus("No results found");
                    return;
                }

                mHandler.post(new Runnable() {
                    public void run() {
                        if (result.title != null && result.title.length() > 0) {
                            mEditTitle.setText(result.title);
                        }
                        if (result.artist != null && result.artist.length() > 0) {
                            mEditArtist.setText(result.artist);
                        }
                        if (result.album != null && result.album.length() > 0) {
                            mEditAlbum.setText(result.album);
                        }
                        if (result.year != null && result.year.length() > 0) {
                            mEditYear.setText(result.year);
                        }
                        mReleaseId = result.releaseId;
                        mStatusText.setText("Info found! Downloading art...");
                    }
                });

                boolean gotArt = false;
                if (result.releaseId != null && result.releaseId.length() > 0
                        && mAlbumId >= 0) {
                    gotArt = MetadataUtils.downloadAlbumArt(
                            EditInfoActivity.this, result.releaseId, mAlbumId);
                }

                final boolean artOk = gotArt;
                mHandler.post(new Runnable() {
                    public void run() {
                        mBtnLookup.setEnabled(true);
                        if (artOk && mAlbumId >= 0) {
                            File artFile = new File(
                                    Environment.getExternalStorageDirectory(),
                                    ".walkman_art/" + mAlbumId + ".jpg");
                            if (artFile.exists()) {
                                Bitmap bm = BitmapFactory.decodeFile(
                                        artFile.getAbsolutePath());
                                if (bm != null) {
                                    mAlbumArt.setImageBitmap(bm);
                                }
                            }
                            mStatusText.setText("Info and art found! Review and save.");
                        } else {
                            loadAlbumArt();
                            mStatusText.setText("Info found! Review and save.");
                        }
                    }
                });
            }
        }).start();
    }

    private void postStatus(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                mBtnLookup.setEnabled(true);
                mStatusText.setText(msg);
            }
        });
    }
}
