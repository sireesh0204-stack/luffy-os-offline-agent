package com.luffy.core;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class AmbientListenTileService extends TileService {
    public static final String PREFS_NAME = "luffy_settings";
    public static final String KEY_AMBIENT_ENABLED = "ambient_listen_enabled";

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean current = prefs.getBoolean(KEY_AMBIENT_ENABLED, false);
        boolean next = !current;
        prefs.edit().putBoolean(KEY_AMBIENT_ENABLED, next).apply();

        refreshTile();

        if (next) {
            Toast.makeText(this, "Luffy: Ambient Listening (Active - 6s/3m)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Luffy: Ambient Listening OFF (0% Battery)", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean active = prefs.getBoolean(KEY_AMBIENT_ENABLED, false);

        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Ambient Listen");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.setSubtitle(active ? "Active" : "Off");
        }
        tile.updateTile();
    }
}
