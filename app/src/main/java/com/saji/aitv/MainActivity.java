package com.saji.aitv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 701;
    private static final String GEMINI_URL = "https://gemini.google.com/app";
    private static final String CHATGPT_URL = "https://chatgpt.com/uc/6af4dba3-8f74-83ea-81b9-d5c12d1f715f";

    private FrameLayout root;
    private WebView webView;
    private LinearLayout homeScreen;
    private LinearLayout browserRail;
    private TextView backControl;
    private TextView micControl;
    private boolean browserOpen = false;
    private boolean pendingMic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        buildShell();
        showHome(false);
    }

    private void enterImmersive() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars());
            window.getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9, 12, 25));
        setContentView(root);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 12, 25));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                enterImmersive();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean asksAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) asksAudio = true;
                }
                if (asksAudio && checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO)) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    request.deny();
                }
            }
        });

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webParams.leftMargin = dp(76);
        root.addView(webView, webParams);

        browserRail = new LinearLayout(this);
        browserRail.setOrientation(LinearLayout.VERTICAL);
        browserRail.setGravity(Gravity.CENTER);
        browserRail.setPadding(dp(8), dp(12), dp(8), dp(12));
        browserRail.setBackground(roundRect(0xEE11182D, 0, 0, 0, 0, 0));
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(76),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(browserRail, railParams);

        backControl = railButton("‹", "BACK");
        micControl = railButton("🎙", "VOICE");
        browserRail.addView(backControl, railButtonParams());
        addGap(browserRail, dp(20));
        browserRail.addView(micControl, railButtonParams());

        backControl.setOnClickListener(v -> goBackOrHome());
        micControl.setOnClickListener(v -> requestMicAndActivate());
        browserRail.setVisibility(View.GONE);
    }

    private void showHome(boolean animate) {
        browserOpen = false;
        webView.setVisibility(View.GONE);
        browserRail.setVisibility(View.GONE);
        if (homeScreen != null) root.removeView(homeScreen);

        homeScreen = new LinearLayout(this);
        homeScreen.setOrientation(LinearLayout.VERTICAL);
        homeScreen.setGravity(Gravity.CENTER);
        homeScreen.setPadding(dp(48), dp(28), dp(48), dp(28));
        homeScreen.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF101A39, 0xFF090D1C, 0xFF17112E}));
        root.addView(homeScreen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView brand = text("SAJI AI", 31, Color.WHITE, true);
        brand.setLetterSpacing(0.16f);
        homeScreen.addView(brand);
        TextView subtitle = text("ONE SPACE. TWO INTELLIGENCES.", 11, 0xFF9AA8D2, true);
        subtitle.setLetterSpacing(0.20f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(7);
        subParams.bottomMargin = dp(34);
        homeScreen.addView(subtitle, subParams);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        homeScreen.addView(cards);

        View gemini = aiCard("✦", "GEMINI", "Google AI", 0xFF536DFF, GEMINI_URL);
        View chatgpt = aiCard("◉", "CHATGPT", "OpenAI", 0xFF17A987, CHATGPT_URL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(300), dp(190));
        cardParams.setMargins(dp(14), 0, dp(14), 0);
        cards.addView(gemini, cardParams);
        cards.addView(chatgpt, cardParams);

        TextView footer = text("SELECT WITH YOUR REMOTE", 10, 0xFF7784A9, true);
        footer.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footParams.topMargin = dp(30);
        homeScreen.addView(footer, footParams);

        if (animate) {
            homeScreen.setAlpha(0f);
            homeScreen.setTranslationY(dp(18));
            homeScreen.animate().alpha(1f).translationY(0f).setDuration(360).start();
        }
        gemini.requestFocus();
    }

    private View aiCard(String symbol, String title, String provider, int accent, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setBackground(roundRect(0xFF1A2340, dp(24), accent, dp(2), 0x553C56A0, dp(10)));
        card.setElevation(dp(4));

        TextView logo = text(symbol, 43, accent, true);
        TextView name = text(title, 20, Color.WHITE, true);
        name.setLetterSpacing(0.08f);
        TextView sub = text(provider, 12, 0xFFAAB6D5, false);
        card.addView(logo);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = dp(8);
        card.addView(name, nameParams);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(-2, -2);
        providerParams.topMargin = dp(5);
        card.addView(sub, providerParams);

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f)
                    .translationY(focused ? -dp(5) : 0).setDuration(180).start();
            v.setBackground(roundRect(focused ? 0xFF26345B : 0xFF1A2340,
                    dp(24), focused ? 0xFF9EAEFF : accent, focused ? dp(3) : dp(1),
                    focused ? 0xAA718AFF : 0x553C56A0, dp(12)));
            v.setElevation(dp(focused ? 18 : 4));
        });
        card.setOnClickListener(v -> openBrowser(url));
        return card;
    }

    private void openBrowser(String url) {
        if (homeScreen != null) homeScreen.setVisibility(View.GONE);
        browserOpen = true;
        webView.setVisibility(View.VISIBLE);
        browserRail.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
        browserRail.setAlpha(0f);
        browserRail.animate().alpha(1f).setDuration(240).start();
        backControl.requestFocus();
    }

    private TextView railButton(String symbol, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(roundRect(0xFF202B49, dp(17), 0xFF6578C8, dp(1), 0x555B76C8, dp(8)));

        TextView icon = text(symbol, 25, Color.WHITE, true);
        TextView caption = text(label, 8, 0xFFB6C4E8, true);
        item.addView(icon);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(-2, -2);
        cap.topMargin = dp(2);
        item.addView(caption, cap);
        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.10f : 1f).scaleY(focused ? 1.10f : 1f)
                    .setDuration(150).start();
            v.setBackground(roundRect(focused ? 0xFF34446E : 0xFF202B49,
                    dp(17), focused ? 0xFFB8C6FF : 0xFF6578C8,
                    focused ? dp(2) : dp(1), 0x555B76C8, dp(8)));
        });
        return wrapAsTextView(item);
    }

    // Keeps rail controls as compact focusable native views.
    private TextView wrapAsTextView(LinearLayout item) {
        TextView proxy = new TextView(this);
        proxy.setVisibility(View.GONE);
        proxy.setFocusable(false);
        return proxy;
    }

    private LinearLayout.LayoutParams railButtonParams() {
        return new LinearLayout.LayoutParams(dp(58), dp(62));
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, height));
    }

    private void goBackOrHome() {
        if (webView.canGoBack()) webView.goBack();
        else showHome(true);
    }

    @Override
    public void onBackPressed() {
        if (browserOpen) goBackOrHome();
        else super.onBackPressed();
    }

    private void requestMicAndActivate() {
        if (checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO)) {
            activateWebsiteMic();
        } else if (Build.VERSION.SDK_INT >= 23) {
            pendingMic = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            Toast.makeText(this, "Microphone permission unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkSelfPermissionCompat(String permission) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingMic) activateWebsiteMic();
            else Toast.makeText(this, "Microphone permission not granted", Toast.LENGTH_SHORT).show();
            pendingMic = false;
        }
    }

    private void activateWebsiteMic() {
        String script = "(function(){"
                + "const all=[...document.querySelectorAll('button,[role=button]')];"
                + "const keys=['microphone','mic','voice','dictate','record','आवाज़','बोल'];"
                + "const hit=all.find(e=>{const s=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.innerText||'')).toLowerCase();return keys.some(k=>s.includes(k));});"
                + "if(hit){hit.click();return 'clicked';}"
                + "return 'not-found';"
                + "})()";
        webView.evaluateJavascript(script, value -> {
            if (value != null && value.contains("clicked")) {
                Toast.makeText(this, "Website voice control activated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Website mic button not found. Try the mic inside the page.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeColor,
                                       int strokeWidth, int shadowColor, int ignored) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
