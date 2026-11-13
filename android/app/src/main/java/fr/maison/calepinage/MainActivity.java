package fr.maison.calepinage;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Calepinage, version Capacitor : la même appli web (www/index.html) dans une vraie appli Android. */
public class MainActivity extends BridgeActivity {
    private static final String LEGACY_FILE = "legacy-localstorage.json";
    private WebView legacyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Le pont window.AndroidApp doit exister avant le chargement de la page : il est posé par AppBridgePlugin.load()
        registerPlugin(AppBridgePlugin.class);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // zoom à deux doigts sur toute l'appli, sans les boutons + / − d'Android
        android.webkit.WebSettings zs = getBridge().getWebView().getSettings();
        zs.setSupportZoom(true); zs.setBuiltInZoomControls(true); zs.setDisplayZoomControls(false);
        readLegacyOnce();
        keepAuth(getIntent());
    }

    private String pendingAuth = "";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (keepAuth(intent)) {
            String u = pendingAuth;
            getBridge().getWebView().evaluateJavascript("window.__oauthReturn&&window.__oauthReturn(" + org.json.JSONObject.quote(u) + ")", null);
        }
    }

    /** Garde l'adresse de retour de la connexion Google pour la page. */
    private boolean keepAuth(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || !"fr.maison.calepinage".equals(d.getScheme())) return false;
        pendingAuth = d.toString();
        return true;
    }

    /** Plugin minimal : pose window.AndroidApp dans la WebView avant que la page se charge. */
    @CapacitorPlugin(name = "AppBridge")
    public static class AppBridgePlugin extends Plugin {
        @Override
        public void load() {
            MainActivity a = (MainActivity) getActivity();
            WebView web = getBridge().getWebView();
            web.setBackgroundColor(0xFF1D63C4);
            web.getSettings().setTextZoom(100);
            web.addJavascriptInterface(a.new JsApi(), "AndroidApp");
        }
    }

    /**
     * Les versions 1.x gardaient les projets dans le stockage de la page file:///android_asset/index.html.
     * Capacitor sert l'appli depuis https://localhost : on relit une seule fois l'ancien stockage
     * et on le passe à la page, qui l'importe si elle ne l'a pas déjà fait.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void readLegacyOnce() {
        SharedPreferences p = getSharedPreferences("calepinage", Context.MODE_PRIVATE);
        if (p.getBoolean("legacyRead", false)) return;
        readLegacy(false);
    }

    /** Relit l'ancien stockage ; manual = demandé depuis le bouton « Reprendre », la page affiche le résultat. */
    @SuppressLint("SetJavaScriptEnabled")
    void readLegacy(boolean manual) {
        SharedPreferences p = getSharedPreferences("calepinage", Context.MODE_PRIVATE);
        if (legacyView != null) { legacyView.destroy(); legacyView = null; }
        legacyView = new WebView(this);
        legacyView.getSettings().setJavaScriptEnabled(true);
        legacyView.getSettings().setDomStorageEnabled(true);
        legacyView.getSettings().setAllowFileAccess(true);
        legacyView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void done(String json) {
                runOnUiThread(() -> {
                    if (json != null) p.edit().putBoolean("legacyRead", true).apply();
                    if (json != null && json.contains("calepinage-murs-v2")) {
                        try (FileOutputStream o = new FileOutputStream(new File(getFilesDir(), LEGACY_FILE))) {
                            o.write(json.getBytes(StandardCharsets.UTF_8));
                        } catch (Exception ignored) { }
                        if (!manual) getBridge().getWebView().evaluateJavascript("window.__legacyImport&&window.__legacyImport(" + json + ")", null);
                    }
                    if (manual) getBridge().getWebView().evaluateJavascript("window.__legacyResult&&window.__legacyResult(" + (json == null || json.isEmpty() ? "null" : json) + ")", null);
                    if (legacyView != null) { legacyView.destroy(); legacyView = null; }
                });
            }
        }, "Legacy");
        legacyView.loadUrl("file:///android_asset/legacy.html");
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show()); }

    /** Fonctions appelées depuis la page (window.AndroidApp), identiques aux versions 1.x. */
    public class JsApi {
        @JavascriptInterface
        public String saveFile(String name, String text) {
            try {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".json") ? "application/json" : "text/plain");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Calepinage");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
                } else {
                    File d = new File(getExternalFilesDir(null), name);
                    try (FileOutputStream o = new FileOutputStream(d)) { o.write(bytes); }
                }
                toast("Enregistré dans Téléchargements/Calepinage : " + name);
                return "ok";
            } catch (Exception e) {
                toast("Enregistrement impossible : " + e.getMessage());
                return "err";
            }
        }
        @JavascriptInterface
        public String saveImage(String name, String b64) {
            try {
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Calepinage");
                    Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
                } else {
                    File d = new File(getExternalFilesDir(null), name);
                    try (FileOutputStream o = new FileOutputStream(d)) { o.write(bytes); }
                }
                toast("Photo enregistrée dans la Galerie (Images/Calepinage)");
                return "ok";
            } catch (Exception e) {
                toast("Photo non enregistrée : " + e.getMessage());
                return "err";
            }
        }
        @JavascriptInterface
        public void print(String title) {
            runOnUiThread(() -> {
                String t = title == null ? "Calepinage" : title;
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                WebView web = getBridge().getWebView();
                web.setBackgroundColor(0xFFFFFFFF); // marges du PDF en blanc, pas le bleu de l'appli
                final PrintDocumentAdapter in = web.createPrintDocumentAdapter(t);
                PrintDocumentAdapter ad = new PrintDocumentAdapter() {
                    @Override public void onStart() { in.onStart(); }
                    @Override public void onLayout(PrintAttributes o, PrintAttributes n, android.os.CancellationSignal c, LayoutResultCallback cb, Bundle b) { in.onLayout(o, n, c, cb, b); }
                    @Override public void onWrite(android.print.PageRange[] p, android.os.ParcelFileDescriptor d, android.os.CancellationSignal c, WriteResultCallback cb) { in.onWrite(p, d, c, cb); }
                    @Override public void onFinish() { in.onFinish(); web.setBackgroundColor(0xFF1D63C4); web.evaluateJavascript("document.body.classList.remove('pdoc','pshop')", null); }
                };
                pm.print(t, ad, new PrintAttributes.Builder().setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
            });
        }
        @JavascriptInterface
        public void share(String title, String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, title)));
        }
        @JavascriptInterface
        public boolean isApp() { return true; }
        /** Ouvre une adresse dans le navigateur du téléphone (connexion Google). */
        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { toast("Impossible d'ouvrir le navigateur"); } });
        }
        /** Retour de connexion arrivé avant le chargement de la page (vide sinon). */
        @JavascriptInterface
        public String takeAuth() { String u = pendingAuth; pendingAuth = ""; return u; }
        @JavascriptInterface
        public void recoverLegacy() { runOnUiThread(() -> readLegacy(true)); }
        @JavascriptInterface
        public String platform() { return "capacitor-android"; }
        /** Projets de l'ancienne appli (vide s'il n'y en a pas). */
        @JavascriptInterface
        public String legacyData() {
            File f = new File(getFilesDir(), LEGACY_FILE);
            if (!f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()]; int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
                return new String(b, 0, n, StandardCharsets.UTF_8);
            } catch (Exception e) { return ""; }
        }
    }
}
