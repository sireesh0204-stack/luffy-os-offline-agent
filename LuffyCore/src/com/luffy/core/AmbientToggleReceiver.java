package com.luffy.core;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class AmbientToggleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.luffy.core.ACTION_AMBIENT_TOGGLE".equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(AmbientListenTileService.PREFS_NAME, Context.MODE_PRIVATE);
            boolean current = prefs.getBoolean(AmbientListenTileService.KEY_AMBIENT_ENABLED, false);
            boolean next = intent.hasExtra("enabled") ? intent.getBooleanExtra("enabled", !current) : !current;
            prefs.edit().putBoolean(AmbientListenTileService.KEY_AMBIENT_ENABLED, next).apply();

            // Request QS Tile state refresh
            try {
                TileService.requestListeningState(context, new ComponentName(context, AmbientListenTileService.class));
            } catch (Exception ignored) {}

            // Show HUD feedback
            if (next) {
                Toast.makeText(context, "♫ Luffy: Ambient Music Listening ON", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "♫ Luffy: Ambient Music Listening OFF", Toast.LENGTH_SHORT).show();
            }
        } else if ("com.luffy.core.ACTION_NOW_PLAYING".equals(intent.getAction())) {
            String track = intent.getStringExtra("track");
            String artist = intent.getStringExtra("artist");
            if (track != null && artist != null) {
                // Broadcast to Samsung AOD & Lockscreen Music Widget
                Intent musicIntent = new Intent("com.android.music.metachanged");
                musicIntent.putExtra("track", track);
                musicIntent.putExtra("artist", artist);
                musicIntent.putExtra("album", "Ambient Recognition");
                musicIntent.putExtra("playing", true);
                context.sendBroadcast(musicIntent);
            }
        }
    }
}
