package com.saji.aitv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
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
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
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
            // Fullscreen is optional; it must never prevent the app from opening.
        }
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080D1B);
        setContentView(root);

        browserRail = new LinearLayout(this);
        browserRail.setOrientation(LinearLayout.VERTICAL);
        browserRail.setGravity(Gravity.CENTER);
        browserRail.setPadding(dp(8), dp(14), dp(8), dp(14));
        browserRail.setBackground(glassPanel(false));
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                dp(82), FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        railParams.topMargin = dp(18);
        railParams.bottomMargin = dp(18);
        root.addView(browserRail, railParams);

        backControl = railButton(false, "BACK");
        micControl = railButton(true, "VOICE");
        browserRail.addView(backControl, new LinearLayout.LayoutParams(
                dp(62), dp(76)));
        addGap(browserRail, dp(18));
        browserRail.addView(micControl, new LinearLayout.LayoutParams(
                dp(62), dp(76)));

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
                new int[]{0xFF111D3C, 0xFF080D1B, 0xFF17112F}));
        root.addView(homeScreen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

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

        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int cardWidthDp = Math.min(330, Math.max(190, (screenWidthDp - 104) / 2 - 16));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                dp(cardWidthDp), dp(184));
        cardParams.setMargins(dp(8), 0, dp(8), 0);
        View geminiCard = aiCard("✦", "GEMINI", "Google AI",
                0xFF8193FF, GEMINI_URL);
        View chatgptCard = aiCard("◉", "CHATGPT", "OpenAI",
                0xFF32CBA5, CHATGPT_URL);
        cards.addView(geminiCard, cardParams);
        LinearLayout.LayoutParams secondCardParams =
                new LinearLayout.LayoutParams(dp(cardWidthDp), dp(184));
        secondCardParams.setMargins(dp(8), 0, dp(8), 0);
        cards.addView(chatgptCard, secondCardParams);
        geminiCard.setNextFocusRightId(chatgptCard.getId());
        chatgptCard.setNextFocusLeftId(geminiCard.getId());

        TextView footer = text("USE YOUR TV REMOTE TO SELECT",
                10, 0xFF7784A9, true);
        footer.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-2, -2);
        footerParams.topMargin = dp(24);
        homeScreen.addView(footer, footerParams);

        if (message != null && !message.isEmpty()) {
            TextView status = text(message, 10, 0xFFFFC27A, false);
            LinearLayout.LayoutParams statusParams =
                    new LinearLayout.LayoutParams(-2, -2);
            statusParams.topMargin = dp(12);
            homeScreen.addView(status, statusParams);
        }

        if (animate) {
            homeScreen.setAlpha(0f);
            homeScreen.setTranslationY(dp(12));
            homeScreen.animate().alpha(1f).translationY(0f)
                    .setDuration(260).start();
        }
        geminiCard.requestFocus();
    }

    private View aiCard(String symbol, String title, String provider,
                        int accent, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setId(View.generateViewId());
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(cardBackground(false, accent));

        TextView logo = text(symbol, 42, accent, true);
        TextView name = text(title, 19, Color.WHITE, true);
        name.setLetterSpacing(0.08f);
        TextView detail = text(provider, 12, 0xFFAAB6D5, false);
        card.addView(logo);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = dp(8);
        card.addView(name, nameParams);
        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(-2, -2);
        detailParams.topMargin = dp(5);
        card.addView(detail, detailParams);

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.055f : 1f)
                    .scaleY(focused ? 1.055f : 1f)
                    .translationY(focused ? -dp(4) : 0)
                    .setDuration(160).start();
            v.setBackground(cardBackground(focused, accent));
            v.setElevation(dp(focused ? 12 : 3));
        });
        card.setOnClickListener(v -> openBrowser(url));
        return card;
    }

    private GradientDrawable cardBackground(boolean focused, int accent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                focused
                        ? new int[]{0xFF2B3B64, 0xFF182442}
                        : new int[]{0xFF202B49, 0xFF151F38});
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(focused ? dp(3) : dp(1),
                focused ? 0xFFCAD4FF : accent);
        return drawable;
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
            showHome("Android System WebView is unavailable. Update or enable WebView/Chrome on this TV.", true);
        }
    }

    private void ensureWebView() {
        if (webView != null) return;

        WebView candidate = new WebView(this);
        candidate.setBackgroundColor(0xFF080D1B);
        candidate.setFocusable(true);
        candidate.setFocusableInTouchMode(true);
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
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua.replace("; wv", "")
                .replace("Version/4.0 ", ""));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cookies.setAcceptThirdPartyCookies(candidate, true);
        }

        candidate.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
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
            public void onReceivedSslError(
                    WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this,
                        "Secure connection failed. Check TV date and internet.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "Page load failed. Check internet and try again.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView view, android.webkit.RenderProcessGoneDetail detail) {
                recoverFromWebViewFailure();
                return true;
            }
        });

        candidate.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean asksAudio = false;
                boolean asksOther = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        asksAudio = true;
                    } else {
                        asksOther = true;
                    }
                }
                if (asksAudio && !asksOther && hasAudioPermission()) {
                    request.grant(new String[]{
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    request.deny();
                }
            }
        });

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        webParams.leftMargin = dp(82);
        root.addView(candidate, 0, webParams);
        candidate.setVisibility(View.GONE);
        webView = candidate;
    }

    private void recoverFromWebViewFailure() {
        try {
            if (webView != null) {
                root.removeView(webView);
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
            webView = null;
        }
        showHome("The web page engine restarted. Select an AI app to try again.", true);
    }

    private LinearLayout railButton(boolean microphone, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setClickable(true);
        item.setBackground(glassButton(false));
        item.setElevation(dp(2));

        IconView icon = new IconView(this, microphone);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(30), dp(30));
        item.addView(icon, iconParams);

        TextView caption = text(label, 8, 0xFFD5DDF6, true);
        caption.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams cap =
                new LinearLayout.LayoutParams(-2, -2);
        cap.topMargin = dp(7);
        item.addView(caption, cap);

        item.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.09f : 1f)
                    .scaleY(focused ? 1.09f : 1f)
                    .setDuration(150).start();
            v.setBackground(glassButton(focused));
            v.setElevation(dp(focused ? 12 : 2));
            icon.setFocusedState(focused);
        });
        return item;
    }

    private GradientDrawable glassPanel(boolean focused) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xE52B3B5C, 0xE811192E});
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), focused ? 0xFF92A9FF : 0x556F82BA);
        return drawable;
    }

    private GradientDrawable glassButton(boolean focused) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                focused
                        ? new int[]{0xFF526A9C, 0xFF26395F}
                        : new int[]{0xD9364A72, 0xD91A2946});
        drawable.setCornerRadius(dp(19));
        drawable.setStroke(focused ? dp(2) : dp(1),
                focused ? 0xFFE0E7FF : 0x667F9AE0);
        return drawable;
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
        if (browserOpen) {
            goBackOrHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP
                && browserOpen) {
            goBackOrHome();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void requestMicAndActivate() {
        if (webView == null) return;
        if (hasAudioPermission()) {
            activateWebsiteMic();
        } else if (Build.VERSION.SDK_INT >= 23) {
            pendingMic = true;
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            Toast.makeText(this, "Microphone permission is not available.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingMic) {
                activateWebsiteMic();
            } else {
                Toast.makeText(this, "Microphone permission was not granted.",
                        Toast.LENGTH_SHORT).show();
            }
            pendingMic = false;
        }
    }

    private void activateWebsiteMic() {
        if (webView == null) return;
        String script = "(function(){"
                + "const nodes=[...document.querySelectorAll('button,[role=button]')];"
                + "const terms=['microphone','voice','dictate','record','start speaking',"
                + "'mic','speech','start voice','use microphone'];"
                + "const el=nodes.find(n=>{"
                + "const s=((n.getAttribute('aria-label')||'')+' '+"
                + "(n.getAttribute('title')||'')+' '+"
                + "(n.getAttribute('data-testid')||'')+' '+"
                + "(n.innerText||n.textContent||'')).toLowerCase();"
                + "return terms.some(t=>s.includes(t));});"
                + "if(el){el.click();return 'ok';}return 'missing';"
                + "})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("ok")) {
                Toast.makeText(this, "Voice control activated.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Use the microphone button inside the AI page.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private class IconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean microphone;
        private final Path path = new Path();
        private boolean focused;

        IconView(Activity activity, boolean microphone) {
            super(activity);
            this.microphone = microphone;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setFocusedState(boolean focused) {
            this.focused = focused;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 48f;
            canvas.save();
            canvas.translate((getWidth() - 48f * scale) / 2f,
                    (getHeight() - 48f * scale) / 2f);
            canvas.scale(scale, scale);

            paint.setColor(focused ? Color.WHITE : 0xFFE8EEFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if (microphone) {
                RectF capsule = new RectF(18, 5, 30, 27);
                canvas.drawRoundRect(capsule, 6, 6, paint);
                path.reset();
                path.moveTo(12, 22);
                path.cubicTo(12, 31, 17, 36, 24, 36);
                path.cubicTo(31, 36, 36, 31, 36, 22);
                canvas.drawPath(path, paint);
                canvas.drawLine(24, 36, 24, 42, paint);
                canvas.drawLine(17, 42, 31, 42, paint);
            } else {
                path.reset();
                path.moveTo(27, 10);
                path.lineTo(13, 24);
                path.lineTo(27, 38);
                path.moveTo(14, 24);
                path.lineTo(37, 24);
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable roundRect(
            int fill, int radius, int strokeColor, int strokeWidth) {
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
