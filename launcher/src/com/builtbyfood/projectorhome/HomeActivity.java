// Projector Home v0.2.0
package com.builtbyfood.projectorhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Home screen for a projector.
 *
 * Roku mode: tiles are Roku channels; a tile launches the channel on the Roku and switches
 *            the projector to the Roku's HDMI input.
 * Apps mode: tiles are apps installed on the projector; plus a tile for the HDMI input.
 *
 * First run asks which mode to use. Long-press Settings for the setup menu.
 */
public class HomeActivity extends Activity {

    private static final int BG = 0xFF0E1116;
    private static final int TILE = 0xFF1C222B;
    private static final int TILE_FOCUS = 0xFF2F6FED;
    private static final int TEXT = 0xFFE8ECF1;
    private static final int TEXT_DIM = 0xFF8A94A3;

    /** Layout constants, in dp. */
    private static final int PAGE_PAD_H = 40;
    private static final int PAGE_PAD_V = 24;
    private static final int TILE_MARGIN = 8;
    private static final int FAV_MAX_W = 230;
    private static final int FAV_MAX_COLS = 6;
    private static final int UTIL_MAX_W = 200;
    private static final int GRID_MIN_W = 150;
    /** Focused tiles grow 7%; leave room so the edge tiles are never clipped. */
    private static final float FOCUS_SCALE = 1.07f;

    /** Roku channel names to show before the Roku has answered the first time. */
    private static final Map<String, String> KNOWN = new HashMap<>();
    static {
        KNOWN.put("12", "Netflix");
        KNOWN.put("13", "Prime Video");
        KNOWN.put("837", "YouTube");
    }

    /** An app installed on the projector. */
    private static final class LocalApp {
        final String pkg;
        final String label;
        final Drawable icon;

        LocalApp(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    private Config cfg;
    private FrameLayout root;
    private TextView status;
    private boolean showingAll;
    private boolean prompted;
    private List<RokuClient.App> channels = new ArrayList<>();
    private Map<String, LocalApp> localApps = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        if (cfg.isRoku() && cfg.hasRokuIp()) channels = client().cachedApps();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!prompted) {
            if (cfg.mode().isEmpty()) {
                prompted = true;
                chooseMode();
                return;
            }
            if (cfg.isRoku() && !cfg.hasRokuIp()) {
                prompted = true;
                editRokuIp();
            }
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (showingAll) showHome();
        // On the home screen Back does nothing: this is the launcher.
    }

    // ---------------------------------------------------------------- data

    private RokuClient client() {
        return new RokuClient(cfg.rokuIp(), new File(getFilesDir(), "roku"));
    }

    private void refresh() {
        if (cfg.mode().isEmpty()) {
            setStatus("Long-press Settings to set up");
            return;
        }
        if (cfg.isRoku()) refreshRoku(); else refreshLocal();
    }

    private void refreshRoku() {
        if (!cfg.hasRokuIp()) {
            setStatus("No Roku address set · long-press Settings to add it");
            return;
        }
        setStatus("Connecting to Roku at " + cfg.rokuIp() + "…");
        io.execute(() -> {
            try {
                List<RokuClient.App> fresh = client().fetchApps();
                main.post(() -> {
                    channels = fresh;
                    setStatus("Roku connected · " + fresh.size() + " channels");
                    redraw();
                });
            } catch (IOException e) {
                main.post(() -> setStatus("Roku not reachable at " + cfg.rokuIp()));
            }
        });
    }

    private void refreshLocal() {
        io.execute(() -> {
            Map<String, LocalApp> apps = queryLocalApps();
            main.post(() -> {
                localApps = apps;
                setStatus(apps.size() + " apps on this projector");
                redraw();
            });
        });
    }

    /** Launchable apps (TV launcher and phone launcher categories), excluding this one. */
    private Map<String, LocalApp> queryLocalApps() {
        PackageManager pm = getPackageManager();
        Map<String, LocalApp> out = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || out.containsKey(pkg)) continue;
                out.put(pkg, new LocalApp(pkg, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
            }
        }
        List<LocalApp> sorted = new ArrayList<>(out.values());
        Collections.sort(sorted, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Map<String, LocalApp> ordered = new LinkedHashMap<>();
        for (LocalApp a : sorted) ordered.put(a.pkg, a);
        return ordered;
    }

    private String channelName(String id) {
        for (RokuClient.App a : channels) if (a.id.equals(id)) return a.name;
        String k = KNOWN.get(id);
        return k != null ? k : "Channel " + id;
    }

    // ---------------------------------------------------------------- actions

    private void openChannel(String id) {
        if (!cfg.hasRokuIp()) { editRokuIp(); return; }
        toast("Opening " + channelName(id));
        io.execute(() -> {
            try {
                client().launch(id);
            } catch (IOException e) {
                main.post(() -> toast("Roku not reachable: " + e.getMessage()));
            }
            main.post(() -> HdmiInput.show(this, cfg.hdmiSource()));
        });
    }

    private void rokuHome() {
        if (!cfg.hasRokuIp()) { editRokuIp(); return; }
        io.execute(() -> {
            try {
                client().keypress("Home");
            } catch (IOException e) {
                main.post(() -> toast("Roku not reachable: " + e.getMessage()));
            }
            main.post(() -> HdmiInput.show(this, cfg.hdmiSource()));
        });
    }

    private void openApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast("Can't open " + pkg); return; }
        startActivity(i);
    }

    private void openInput() {
        if (!HdmiInput.playerInstalled(this)) {
            toast("No HiSilicon input player on this projector");
            return;
        }
        HdmiInput.show(this, cfg.hdmiSource());
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) i = new Intent(Settings.ACTION_SETTINGS);
        startActivity(i);
    }

    // ---------------------------------------------------------------- setup dialogs

    private void chooseMode() {
        new AlertDialog.Builder(this)
                .setTitle("How do you watch?")
                .setItems(new String[]{
                        "Roku plugged into this projector's HDMI",
                        "Apps installed on this projector"
                }, (d, which) -> {
                    cfg.setMode(which == 0 ? Config.MODE_ROKU : Config.MODE_APPS);
                    if (which == 0 && !cfg.hasRokuIp()) editRokuIp();
                    showHome();
                    refresh();
                })
                .setCancelable(false)
                .show();
    }

    private void setup() {
        String modeLine = "Watching with: " + (cfg.isRoku() ? "Roku over HDMI" : "apps on this projector");
        String inputLine = "Streaming input: " + (cfg.hdmiSource() >= 0
                ? "source " + cfg.hdmiSource() : "not set (shows last-used input)");
        List<String> items = new ArrayList<>();
        items.add(modeLine + " · change");
        if (cfg.isRoku()) items.add("Roku address: " + (cfg.hasRokuIp() ? cfg.rokuIp() : "not set"));
        items.add(inputLine);
        items.add("Android settings");
        final boolean roku = cfg.isRoku();
        new AlertDialog.Builder(this)
                .setTitle("Projector Home " + versionName())
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    int i = which;
                    if (i == 0) { chooseMode(); return; }
                    if (roku) { if (i == 1) { editRokuIp(); return; } i--; }
                    if (i == 1) { pickInput(); return; }
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                })
                .show();
    }

    private void pickInput() {
        int last = HdmiInput.lastSelectedSource();
        String msg = "First switch to your streaming device's input with the projector remote, "
                + "then come back here and choose \"Use last-watched input\".\n\n"
                + (last >= 0 ? "Last-watched input: source " + last : "This projector doesn't report its input.");
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Streaming device input")
                .setMessage(msg)
                .setNeutralButton("Clear", (d, w) -> {
                    cfg.setHdmiSource(-1);
                    toast("Input cleared: the last-used input will be shown");
                })
                .setNegativeButton("Cancel", null);
        if (last >= 0) {
            b.setPositiveButton("Use last-watched input", (d, w) -> {
                cfg.setHdmiSource(last);
                toast("Streaming input set to source " + last);
            });
        }
        b.show();
    }

    private void editRokuIp() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint("e.g. 192.168.1.50");
        in.setText(cfg.rokuIp());
        new AlertDialog.Builder(this)
                .setTitle("Roku IP address")
                .setMessage("Find it on the Roku under Settings → Network → About.")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    String ip = in.getText().toString().trim();
                    if (ip.isEmpty()) return;
                    cfg.setRokuIp(ip);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- sizing

    /** Usable width in dp inside the page padding, minus headroom for the focus zoom. */
    private int contentWidthDp() {
        int screen = getResources().getConfiguration().screenWidthDp;
        int inner = screen - 2 * PAGE_PAD_H;
        return Math.round(inner / FOCUS_SCALE);
    }

    /** Width of one tile when {@code cols} tiles share the row, capped at {@code maxW}. */
    private int tileWidthDp(int cols, int maxW) {
        int slot = contentWidthDp() / Math.max(cols, 1);
        return Math.max(60, Math.min(maxW, slot - 2 * TILE_MARGIN));
    }

    // ---------------------------------------------------------------- screens

    private void redraw() {
        if (showingAll) showAll(); else showHome();
    }

    private void showHome() {
        showingAll = false;
        boolean roku = cfg.isRoku();
        LinearLayout page = page();
        page.addView(header("Projector Home"));

        // Favorites row. In apps mode, skip favorites that aren't installed.
        List<String> favIds = new ArrayList<>();
        for (String id : cfg.favorites()) {
            if (roku || localApps.isEmpty() || localApps.containsKey(id)) favIds.add(id);
        }
        if (!favIds.isEmpty()) {
            int cols = Math.min(favIds.size(), FAV_MAX_COLS);
            int w = tileWidthDp(cols, FAV_MAX_W);
            int h = Math.round(w * 0.82f);
            GridLayout favs = grid(cols);
            for (String id : favIds) favs.addView(favoriteTile(id, w, h));
            page.addView(favs);
        } else if (!cfg.mode().isEmpty()) {
            page.addView(hint(roku ? "No channels on home yet · open All Channels and long-press to add"
                                   : "No apps on home yet · open All Apps and long-press to add"));
        }

        int uw = tileWidthDp(4, UTIL_MAX_W);
        int uh = Math.max(56, Math.round(uw * 0.42f));
        GridLayout utils = grid(4);
        if (roku) {
            utils.addView(tile("Roku Home", uw, uh, v -> rokuHome(), null, null));
            utils.addView(tile("All Channels", uw, uh, v -> showAll(), null, null));
        } else {
            utils.addView(tile("HDMI Input", uw, uh, v -> openInput(), null, null));
            utils.addView(tile("All Apps", uw, uh, v -> showAll(), null, null));
        }
        utils.addView(tile("Keystone / Focus", uw, uh,
                v -> openPackage("com.kystar.projector.settings"), null, null));
        utils.addView(tile("Settings", uw, uh,
                v -> startActivity(new Intent(Settings.ACTION_SETTINGS)),
                v -> { setup(); return true; }, null));
        page.addView(utils);

        page.addView(hint("Long-press a tile to remove it from home · Long-press Settings for setup"));
        page.addView(status);
        swap(page);
    }

    private View favoriteTile(String id, int w, int h) {
        if (cfg.isRoku()) {
            return tile(channelName(id), w, h, v -> openChannel(id), v -> {
                cfg.toggleFavorite(id);
                toast(channelName(id) + " removed from home");
                showHome();
                return true;
            }, iv -> loadRokuIcon(id, iv));
        }
        LocalApp a = localApps.get(id);
        String label = a != null ? a.label : id;
        return tile(label, w, h, v -> openApp(id), v -> {
            cfg.toggleFavorite(id);
            toast(label + " removed from home");
            showHome();
            return true;
        }, iv -> { if (a != null) iv.setImageDrawable(a.icon); });
    }

    private void showAll() {
        showingAll = true;
        boolean roku = cfg.isRoku();
        LinearLayout page = page();
        page.addView(header(roku ? "All Channels" : "All Apps"));

        int cols = Math.max(3, contentWidthDp() / (GRID_MIN_W + 2 * TILE_MARGIN));
        int w = tileWidthDp(cols, 220);
        int h = Math.round(w * 0.88f);
        GridLayout grid = grid(cols);

        if (roku) {
            for (RokuClient.App a : channels) {
                String label = (cfg.isFavorite(a.id) ? "★ " : "") + a.name;
                grid.addView(tile(label, w, h, v -> openChannel(a.id), v -> {
                    boolean added = cfg.toggleFavorite(a.id);
                    toast(a.name + (added ? " added to home" : " removed from home"));
                    showAll();
                    return true;
                }, iv -> loadRokuIcon(a.id, iv)));
            }
            if (channels.isEmpty()) page.addView(hint("No channel list yet — is the Roku on and its address set?"));
        } else {
            for (LocalApp a : localApps.values()) {
                String label = (cfg.isFavorite(a.pkg) ? "★ " : "") + a.label;
                grid.addView(tile(label, w, h, v -> openApp(a.pkg), v -> {
                    boolean added = cfg.toggleFavorite(a.pkg);
                    toast(a.label + (added ? " added to home" : " removed from home"));
                    showAll();
                    return true;
                }, iv -> iv.setImageDrawable(a.icon)));
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        sv.addView(grid);
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(hint("Long-press to add or remove from home · Back returns"));
        page.addView(status);
        swap(page);
    }

    // ---------------------------------------------------------------- view helpers

    private void swap(LinearLayout page) {
        root.removeAllViews();
        root.addView(page);
        View first = page.findFocus();
        if (first == null) {
            ArrayList<View> focusables = page.getFocusables(View.FOCUS_FORWARD);
            if (!focusables.isEmpty()) focusables.get(0).requestFocus();
        }
    }

    /** New page container; detaches the shared status line from the previous page. */
    private LinearLayout page() {
        if (status == null) {
            status = text("", 14, TEXT_DIM);
        } else if (status.getParent() != null) {
            ((ViewGroup) status.getParent()).removeView(status);
        }
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(PAGE_PAD_H), dp(PAGE_PAD_V), dp(PAGE_PAD_H), dp(PAGE_PAD_V));
        p.setClipChildren(false);
        p.setClipToPadding(false);
        return p;
    }

    /** Centered grid that lets focused tiles scale past their cell. */
    private GridLayout grid(int cols) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(cols);
        g.setClipChildren(false);
        g.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        g.setLayoutParams(lp);
        return g;
    }

    private View header(String title) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 26, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        h.addView(clock);
        h.setPadding(dp(TILE_MARGIN), 0, dp(TILE_MARGIN), dp(12));
        return h;
    }

    /**
     * A focusable tile. {@code icon} is null for text-only tiles; otherwise it is handed the
     * ImageView to fill (synchronously for local apps, asynchronously for Roku channels).
     */
    private View tile(String label, int wDp, int hDp, View.OnClickListener click,
                      View.OnLongClickListener longClick, Consumer<ImageView> icon) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(tileBackground());
        int pad = Math.max(6, wDp / 18);
        t.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(wDp);
        lp.height = dp(hDp);
        lp.setMargins(dp(TILE_MARGIN), dp(TILE_MARGIN), dp(TILE_MARGIN), dp(TILE_MARGIN));
        t.setLayoutParams(lp);

        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            icon.accept(iv);
        }
        TextView tv = text(label, icon != null ? 14 : 16, TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(0, dp(4), 0, 0);
        t.addView(tv);

        t.setOnClickListener(click);
        if (longClick != null) t.setOnLongClickListener(longClick);
        t.setOnFocusChangeListener((v, f) ->
                v.animate().scaleX(f ? FOCUS_SCALE : 1f).scaleY(f ? FOCUS_SCALE : 1f)
                        .setDuration(120).start());
        return t;
    }

    private void loadRokuIcon(String id, ImageView iv) {
        if (!cfg.hasRokuIp()) return;
        io.execute(() -> {
            Bitmap bmp = client().icon(id);
            if (bmp != null) main.post(() -> iv.setImageBitmap(bmp));
        });
    }

    private StateListDrawable tileBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(TILE_FOCUS));
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(TILE_FOCUS));
        s.addState(new int[]{}, rounded(TILE));
        return s;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private TextView hint(String s) {
        TextView t = text(s, 13, TEXT_DIM);
        t.setPadding(dp(TILE_MARGIN), dp(10), dp(TILE_MARGIN), dp(2));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
