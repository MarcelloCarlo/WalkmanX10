package com.walkman.x10mini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;

            String action = null;
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                action = MusicService.ACTION_TOGGLE;
            } else if (keyCode == 126) { // KEYCODE_MEDIA_PLAY, API 11+
                action = MusicService.ACTION_PLAY;
            } else if (keyCode == 127) { // KEYCODE_MEDIA_PAUSE, API 11+
                action = MusicService.ACTION_PAUSE;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                action = MusicService.ACTION_NEXT;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                action = MusicService.ACTION_PREV;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
                action = MusicService.ACTION_STOP;
            }

            if (action != null) {
                Intent si = new Intent(context, MusicService.class);
                si.setAction(action);
                context.startService(si);
            }
        }
    }
}
