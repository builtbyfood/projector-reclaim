// Projector Home v0.2.0
package com.builtbyfood.projectorhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * HDMI input switching for HiSilicon TV firmware.
 *
 * Uses the vendor TV API (shared library com.hisilicon.android.tvapi) through reflection:
 *   HitvManager.getInstance().getSourceManager().selectSource(id, 0)
 *   HitvManager.getInstance().getSourceManager().getSelectSourceId()
 * then opens HiTvPlayer (com.hisilicon.tvui), which displays the selected source.
 * Verified on a WiMiUS P63 (Hi3751V350): "select source to [10]HDMI2 finish!".
 *
 * Other chipsets need a different player activity and API; see docs/removal-guide.md §4.
 */
final class HdmiInput {
    private static final String TAG = "ProjectorHome";
    static final String PLAYER_PKG = "com.hisilicon.tvui";
    static final String PLAYER_CLS = "com.hisilicon.tvui.MainActivity";

    private HdmiInput() {}

    /** Selects {@code sourceId} (if >= 0) and brings the input player to the front. */
    static void show(Context ctx, int sourceId) {
        if (sourceId >= 0) {
            boolean ok = selectSource(sourceId);
            Log.i(TAG, "selectSource(" + sourceId + ") -> " + ok);
        }
        Intent i = new Intent();
        i.setClassName(PLAYER_PKG, PLAYER_CLS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Input player not found (not a HiSilicon firmware?)", e);
        }
    }

    /** True if this firmware has the HiSilicon input player. */
    static boolean playerInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PLAYER_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The input most recently selected on the projector (e.g. with the remote), or -1. */
    static int lastSelectedSource() {
        try {
            Object src = sourceManager();
            Object id = src.getClass().getMethod("getSelectSourceId").invoke(src);
            return (Integer) id;
        } catch (Throwable t) {
            Log.w(TAG, "getSelectSourceId unavailable: " + t);
            return -1;
        }
    }

    private static boolean selectSource(int sourceId) {
        try {
            Object src = sourceManager();
            src.getClass().getMethod("selectSource", int.class, int.class).invoke(src, sourceId, 0);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "selectSource unavailable: " + t);
            return false;
        }
    }

    private static Object sourceManager() throws Exception {
        Class<?> mgrCls = Class.forName("com.hisilicon.android.tvapi.HitvManager");
        Object mgr = mgrCls.getMethod("getInstance").invoke(null);
        return mgrCls.getMethod("getSourceManager").invoke(mgr);
    }
}
