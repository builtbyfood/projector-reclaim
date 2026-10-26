// Projector Home v0.2.0
package com.builtbyfood.projectorhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Minimal Roku External Control Protocol (ECP) client. LAN only, port 8060. */
final class RokuClient {

    static final class App {
        final String id;
        final String name;

        App(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final String base;
    private final File cacheDir;

    RokuClient(String ip, File cacheDir) {
        this.base = "http://" + ip + ":8060";
        this.cacheDir = cacheDir;
        cacheDir.mkdirs();
    }

    /** Fetches the installed channel list from the Roku and caches it. */
    List<App> fetchApps() throws IOException {
        byte[] xml = get("/query/apps");
        write(new File(cacheDir, "apps.xml"), xml);
        return parse(xml);
    }

    /** Last known channel list, for instant startup or when the Roku is unreachable. */
    List<App> cachedApps() {
        File f = new File(cacheDir, "apps.xml");
        if (!f.exists()) return new ArrayList<>();
        try {
            return parse(read(f));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** Channel icon, fetched once from the Roku and cached on disk. */
    Bitmap icon(String id) {
        File f = new File(cacheDir, "icon_" + id.replaceAll("[^A-Za-z0-9_]", "_"));
        if (!f.exists()) {
            try {
                write(f, get("/query/icon/" + id));
            } catch (IOException e) {
                return null;
            }
        }
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    void launch(String id) throws IOException {
        post("/launch/" + id);
    }

    void keypress(String key) throws IOException {
        post("/keypress/" + key);
    }

    private static List<App> parse(byte[] xml) throws IOException {
        List<App> out = new ArrayList<>();
        try {
            XmlPullParser x = Xml.newPullParser();
            x.setInput(new ByteArrayInputStream(xml), "UTF-8");
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
                if (ev == XmlPullParser.START_TAG && "app".equals(x.getName())
                        && "appl".equals(x.getAttributeValue(null, "type"))) {
                    String id = x.getAttributeValue(null, "id");
                    String name = x.nextText();
                    if (id != null) out.add(new App(id, name == null ? id : name.trim()));
                }
            }
        } catch (Exception e) {
            throw new IOException("Bad app list", e);
        }
        return out;
    }

    private byte[] get(String path) throws IOException {
        HttpURLConnection c = open(path, "GET");
        try {
            int rc = c.getResponseCode();
            if (rc != 200) throw new IOException("HTTP " + rc);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private void post(String path) throws IOException {
        HttpURLConnection c = open(path, "POST");
        try {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            c.getOutputStream().close();
            int rc = c.getResponseCode();
            if (rc < 200 || rc >= 300) throw new IOException("HTTP " + rc);
        } finally {
            c.disconnect();
        }
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        c.setUseCaches(false);
        return c;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        } finally {
            in.close();
        }
        return b.toByteArray();
    }

    private static byte[] read(File f) throws IOException {
        return readAll(new FileInputStream(f));
    }

    private static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream o = new FileOutputStream(tmp);
        try {
            o.write(data);
        } finally {
            o.close();
        }
        if (!tmp.renameTo(f)) throw new IOException("rename failed: " + f);
    }
}
