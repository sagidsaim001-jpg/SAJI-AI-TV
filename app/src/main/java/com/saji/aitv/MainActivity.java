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
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 701;
    private static final String GEMINI_URL = "https://gemini.google.com/app";
    private static final String CHATGPT_URL = "https://chatgpt.com/uc/6af4dba3-8f74-83ea-81b9-d5c12d1f715f";

    private FrameLayout root;
    private LinearLayout homeScreen;
    private LinearLayout browserRail;
    private LinearLayout backControl;
    private LinearLayout micControl;
    private WebView webView;
    private TextView homeMessage;
    private boolean browserOpen;
    private boolean pendingMic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        buildShell();
        showHome(null, false);
    }

    private void enterImmersive() {
        try {
            Window window = getWindow();
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE == 0
                            ? 0 : android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {
            // Fullscreen is optional. A device-specific insets issue must not close the app.
        }
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF090D1C);
        setContentView(root);

        browserRail = new LinearLayout(this);
        browserRail.setOrientation(LinearLayout.VERTICAL);
        browserRail.setGravity(Gravity.CENTER);
        browserRail.setPadding(dp(8), dp(12), dp(8), dp(12));
        browserRail.setBackground(roundRect(0xF011182D, dp(18), 0, 0));
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                dp(76), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(browserRail, railParams);

        backControl = railButton("‹", "BACK");
        micControl = railButton("MIC", "VOICE");
        browserRail.addView(backControl, new LinearLayout.LayoutParams(dp(58), dp(64)));
        addGap(browserRail, dp(20));
        browserRail.addView(micControl, new LinearLayout.LayoutParams(dp(58), dp(64)));
        backControl.setOnClickListener(v -> goBackOrHome());
        micControl.setOnClickListener(v -> requestMicAndActivate());
        browserRail.setVisibility(View.GONE);
    }

    private void showHome(String message, boolean animate) {
        browserOpen = false;
        if (webView != null) webView.setVisibility(View.GONE);
        browserRail.setVisibility(View.GONE);
        if (homeScreen != null) root.removeView(homeScreen);

        homeScreen = new LinearLayout(this);
        homeScreen.setOrientation(LinearLayout.VERTICAL);
        homeScreen.setGravity(Gravity.CENTER);
        homeScreen.setPadding(dp(24), dp(24), dp(24), dp(24));
        homeScreen.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF101A39, 0xFF090D1C, 0xFF17112E}));
        root.addView(homeScreen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView brand = text("SAJI AI", 32, Color.WHITE, true);
        brand.setLetterSpacing(0.15f);
        homeScreen.addView(brand);

        TextView subtitle = text("YOUR AI SPACE", 11, 0xFF9AA8D2, true);
        subtitle.setLetterSpacing(0.22f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(8);
        subParams.bottomMargin = dp(28);
        homeScreen.addView(subtitle, subParams);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        homeScreen.addView(cards);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.min(dp(330), Math.max(dp(190), (screenWidth - dp(100)) / 2 - dp(12)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, dp(184));
        cardParams.setMargins(dp(8), 0, dp(8), 0);
        cards.addView(aiCard("✦", "GEMINI", "Google AI", 0xFF7187FF, GEMINI_URL), cardParams);
        LinearLayout.LayoutParams cardParams2 = new LinearLayout.LayoutParams(cardWidth, dp(184));
        cardParams2.setMargins(dp(8), 0, dp(8), 0);
        cards.addView(aiCard("◉", "CHATGPT", "OpenAI", 0xFF29C49C, CHATGPT_URL), cardParams2);

        TextView footer = text("USE YOUR TV REMOTE TO SELECT", 10, 0xFF7784A9, true);
        footer.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-2, -2);
        footerParams.topMargin = dp(24);
        homeScreen.addView(footer, footerParams);

        homeMessage = text(message == null ? "" : message, 10, 0xFFFFC27A, false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-2, -2);
        messageParams.topMargin = dp(12);
        homeScreen.addView(homeMessage, messageParams);

        if (animate) {
            homeScreen.setAlpha(0f);
            homeScreen.setTranslationY(dp(12));
            homeScreen.animate().alpha(1f).translationY(0f).setDuration(260).start();
        }
        cards.getChildAt(0).requestFocus();
    }

    private View aiCard(String symbol, String title, String provider, int accent, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(roundRect(0xFF1A2340, dp(22), accent, dp(1)));

        TextView logo = text(symbol, 42, accent, true);
        TextView name = text(title, 19, Color.WHITE, true);
        name.setLetterSpacing(0.08f);
        TextView detail = text(provider, 12, 0xFFAAB6D5, false);
        card.addView(logo);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = dp(8);
        card.addView(name, nameParams);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-2, -2);
        detailParams.topMargin = dp(5);
        card.addView(detail, detailParams);

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .translationY(focused ? -dp(4) : 0).setDuration(160).start();
            v.setBackground(roundRect(focused ? 0xFF29375D : 0xFF1A2340, dp(22),
                    focused ? 0xFFBBC7FF : accent, focused ? dp(3) : dp(1)));
            v.setElevation(dp(focused ? 12 : 3));
        });
        card.setOnClickListener(v -> openBrowser(url));
        return card;
    }

    private void openBrowser(String url) {
        try {
            ensureWebView();
            if (homeScreen != null) homeScreen.setVisibility(View.GONE);
            browserOpen = true;
            webView.setVisibility(View.VISIBLE);
            browserRail.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
            browserRail.setAlpha(0f);
            browserRail.animate().alpha(1f).setDuration(220).start();
            backControl.requestFocus();
        } catch (Throwable error) {
            browserOpen = false;
            showHome("This TV has no working Android System WebView. Enable or update Android System WebView/Chrome.", true);
        }
    }

    private void ensureWebView() {
        if (webView != null) return;

        WebView candidate = new WebView(this);
        candidate.setBackgroundColor(0xFF090D1C);
        WebSettings settings = candidate.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));

        candidate.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                enterImmersive();
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this, "Secure connection failed. Check TV date and internet.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "Page load failed. Check internet and try again.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                recoverFromWebViewFailure();
                return true;
            }
        });

        candidate.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean asksAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) asksAudio = true;
                }
                if (asksAudio && hasAudioPermission()) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    request.deny();
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                super.onPermissionRequestCanceled(request);
            }
        });

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webParams.leftMargin = dp(76);
        root.addView(candidate, 0, webParams);
        candidate.setVisibility(View.GONE);
        webView = candidate;
    }

    private void recoverFromWebViewFailure() {
        try {
            if (webView != null) {
                root.removeView(webView);
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
            webView = null;
        }
        showHome("Web page engine restarted. Select Gemini or ChatGPT to try again.", true);
    }

    private LinearLayout railButton(String symbol, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(roundRect(0xFF202B49, dp(16), 0xFF6578C8, dp(1)));

        TextView icon = text(symbol, 22, Color.WHITE, true);
        TextView caption = text(label, 8, 0xFFB6C4E8, true);
        item.addView(icon);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(-2, -2);
        cap.topMargin = dp(3);
        item.addView(caption, cap);
        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f)
                    .setDuration(140).start();
            v.setBackground(roundRect(focused ? 0xFF34446E : 0xFF202B49, dp(16),
                    focused ? 0xFFB8C6FF : 0xFF6578C8, focused ? dp(2) : dp(1)));
        });
        return item;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, height));
    }

    private void goBackOrHome() {
        if (browserOpen && webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            showHome(null, true);
        }
    }

    @Override
    public void onBackPressed() {
        if (browserOpen) goBackOrHome();
        else super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (browserOpen) {
                goBackOrHome();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void requestMicAndActivate() {
        if (webView == null) return;
        if (hasAudioPermission()) {
            activateWebsiteMic();
        } else if (Build.VERSION.SDK_INT >= 23) {
            pendingMic = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            Toast.makeText(this, "Microphone permission is not available.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingMic) activateWebsiteMic();
            else Toast.makeText(this, "Microphone permission was not granted.", Toast.LENGTH_SHORT).show();
            pendingMic = false;
        }
    }

    private void activateWebsiteMic() {
        if (webView == null) return;
        String script = "(function(){"
                + "const nodes=[...document.querySelectorAll('button,[role=button]')];"
                + "const terms=['microphone','voice','dictate','record','start speaking','mic'];"
                + "const el=nodes.find(n=>{const s=((n.getAttribute('aria-label')||'')+' '+(n.getAttribute('title')||'')+' '+(n.innerText||'')).toLowerCase();return terms.some(t=>s.includes(t));});"
                + "if(el){el.click();return 'ok';}return 'missing';"
                + "})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("ok")) {
                Toast.makeText(this, "Voice button selected.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Use the microphone button shown on the AI page.", Toast.LENGTH_LONG).show();
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

    private GradientDrawable roundRect(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        try {
            if (webView != null) webView.onResume();
        } catch (Throwable ignored) {
            recoverFromWebViewFailure();
        }
    }

    @Override
    protected void onPause() {
        try {
            if (webView != null) webView.onPause();
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
