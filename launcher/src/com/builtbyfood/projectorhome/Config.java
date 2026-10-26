// Projector Home v0.2.0
package com.builtbyfood.projectorhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent settings.
 *
 * Two modes:
 *   roku — tiles are Roku channels; selecting one launches it on the Roku (ECP) and
 *          switches the projector to the Roku's HDMI input.
 *   apps — tiles are apps installed on the projector itself.
 */
final class Config {
    static final String MODE_ROKU = "roku";
    static final String MODE_APPS = "apps";

    /** Netflix, Prime Video, YouTube (Roku channel IDs). */
    static final String DEFAULT_ROKU_FAVS = "12,13,837";
    /** Common streaming apps; only those actually installed are shown. */
    static final String DEFAULT_APP_FAVS =
            "com.netflix.mediaclient,com.amazon.avod.thirdpartyclient,"
            + "com.google.android.youtube.tv,com.disney.disneyplus";

    private final SharedPreferences p;

    Config(Context c) {
        p = c.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    // ---- mode

    /** "" until chosen. Installs from 0.1.x that already have a Roku address count as roku mode. */
    String mode() {
        String m = p.getString("mode", "");
        if (m.isEmpty() && hasRokuIp()) return MODE_ROKU;
        return m;
    }

    boolean isRoku() {
        return MODE_ROKU.equals(mode());
    }

    void setMode(String m) {
        p.edit().putString("mode", m).apply();
    }

    // ---- roku

    String rokuIp() {
        return p.getString("roku_ip", "");
    }

    boolean hasRokuIp() {
        return !rokuIp().trim().isEmpty();
    }

    void setRokuIp(String ip) {
        p.edit().putString("roku_ip", ip.trim()).apply();
    }

    // ---- HDMI input

    /** HiSilicon SourceManager id of the streaming device's input, or -1 if not set. */
    int hdmiSource() {
        return p.getInt("hdmi_source", -1);
    }

    void setHdmiSource(int id) {
        p.edit().putInt("hdmi_source", id).apply();
    }

    // ---- favorites (roku channel IDs or app package names, depending on mode)

    private String favKey() {
        return isRoku() ? "favorites" : "app_favorites";
    }

    private String favDefault() {
        return isRoku() ? DEFAULT_ROKU_FAVS : DEFAULT_APP_FAVS;
    }

    List<String> favorites() {
        List<String> out = new ArrayList<>();
        for (String s : p.getString(favKey(), favDefault()).split(",")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    boolean isFavorite(String id) {
        return favorites().contains(id);
    }

    /** Adds or removes a favorite in the current mode; returns true if it is now a favorite. */
    boolean toggleFavorite(String id) {
        List<String> f = favorites();
        boolean added = !f.remove(id);
        if (added) f.add(id);
        p.edit().putString(favKey(), TextUtils.join(",", f)).apply();
        return added;
    }
}
