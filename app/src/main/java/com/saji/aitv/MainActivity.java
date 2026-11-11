package com.saji.aitv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.ArrayList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
    private LinearLayout sendControl;
    private LinearLayout scrollUpControl;
    private LinearLayout scrollDownControl;
    private LinearLayout siteControl;
    private WebView webView;
    private boolean browserOpen;
    private boolean pendingMic;
    private boolean micActivationPending;
    private PermissionRequest pendingWebPermissionRequest;
    private AlertDialog pendingPermissionDialog;
    private String pendingWebsiteKey;
    private boolean rememberPendingWebsitePermission;
    private SpeechRecognizer speechRecognizer;
    private boolean nativeSpeechListening;

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

        backControl = railButton(0, "BACK");
        micControl = railButton(1, "VOICE");
        sendControl = railButton(3, "SEND");
        scrollUpControl = railButton(4, "UP");
        scrollDownControl = railButton(5, "DOWN");
        siteControl = railButton(2, "SITE");
        browserRail.addView(backControl, new LinearLayout.LayoutParams(
                dp(62), dp(76)));
        addGap(browserRail, dp(10));
        browserRail.addView(micControl, new LinearLayout.LayoutParams(
                dp(62), dp(76)));
        addGap(browserRail, dp(8));
        browserRail.addView(sendControl, new LinearLayout.LayoutParams(
                dp(62), dp(70)));
        addGap(browserRail, dp(6));
        browserRail.addView(scrollUpControl, new LinearLayout.LayoutParams(
                dp(62), dp(66)));
        addGap(browserRail, dp(5));
        browserRail.addView(scrollDownControl, new LinearLayout.LayoutParams(
                dp(62), dp(66)));
        addGap(browserRail, dp(6));
        browserRail.addView(siteControl, new LinearLayout.LayoutParams(
                dp(62), dp(70)));

        backControl.setOnClickListener(v -> goBackOrHome());
        micControl.setOnClickListener(v -> requestMicAndActivate());
        sendControl.setOnClickListener(v -> sendCurrentMessage());
        scrollUpControl.setOnClickListener(v -> scrollAiScreen(-1));
        scrollDownControl.setOnClickListener(v -> scrollAiScreen(1));
        siteControl.setOnClickListener(v -> openWebsitePermissionSettings());
        backControl.setNextFocusDownId(micControl.getId());
        micControl.setNextFocusUpId(backControl.getId());
        micControl.setNextFocusDownId(sendControl.getId());
        sendControl.setNextFocusUpId(micControl.getId());
        sendControl.setNextFocusDownId(scrollUpControl.getId());
        scrollUpControl.setNextFocusUpId(sendControl.getId());
        scrollUpControl.setNextFocusDownId(scrollDownControl.getId());
        scrollDownControl.setNextFocusUpId(scrollUpControl.getId());
        scrollDownControl.setNextFocusDownId(siteControl.getId());
        siteControl.setNextFocusUpId(scrollDownControl.getId());
        browserRail.setVisibility(View.GONE);
    }

    private void showHome(String message, boolean animate) {
        stopWebViewMic();
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
            stopWebViewMic();
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
                installMicLifecycleScript(view);
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
                runOnUiThread(() -> handleWebsitePermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (pendingWebPermissionRequest == request) {
                        dismissPendingPermissionDialog();
                        pendingWebPermissionRequest = null;
                        pendingWebsiteKey = null;
                    }
                });
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

    private LinearLayout railButton(int iconMode, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setClickable(true);
        item.setId(View.generateViewId());
        item.setBackground(glassButton(false));
        item.setElevation(dp(2));

        IconView icon = new IconView(this, iconMode);
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
        stopWebViewMic();
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



    // Scroll the active AI conversation area without requiring a mouse.
    private void scrollAiScreen(int direction) {
        if (webView == null || !browserOpen) return;
        String script = "(function(){try{"
                + "var dir=" + "(directionPlaceholder)" + ";"
                + "var active=document.activeElement;"
                + "var candidates=[];"
                + "var nodes=document.querySelectorAll('main,[role=main],"
                + "[data-testid*=conversation],[class*=conversation],"
                + "[class*=chat],[class*=scroll],div,section');"
                + "for(var i=0;i<nodes.length;i++){var el=nodes[i];"
                + "if(!el||el===document.body||el===document.documentElement)continue;"
                + "var r=el.getBoundingClientRect();"
                + "if(r.width<100||r.height<100||r.bottom<0||r.top>innerHeight)continue;"
                + "var st=getComputedStyle(el);"
                + "var can=el.scrollHeight>el.clientHeight+30;"
                + "if(!can)continue;"
                + "var oy=st.overflowY||'';"
                + "var semantic=(el.tagName==='MAIN'||el.getAttribute('role')==='main'"
                + "||((el.className||'')+'').toLowerCase().match(/conversation|chat|scroll/));"
                + "var area=Math.min(r.width,innerWidth)*Math.min(r.height,innerHeight);"
                + "var score=area+(semantic?500000:0);"
                + "if(active&&el.contains(active))score+=200000;"
                + "if(oy==='hidden')score-=100000;"
                + "candidates.push({el:el,score:score});}"
                + "candidates.sort(function(a,b){return b.score-a.score;});"
                + "var target=candidates.length?candidates[0].el:null;"
                + "if(target){var amount=Math.max(260,Math.floor(target.clientHeight*0.68));"
                + "target.scrollBy({top:dir*amount,behavior:'smooth'});"
                + "return 'scrolled';}"
                + "window.scrollBy({top:dir*Math.max(300,Math.floor(innerHeight*0.65)),behavior:'smooth'});"
                + "return 'page';}catch(e){return 'error';}})()";
        script = script.replace("(directionPlaceholder)", direction < 0 ? "-1" : "1");
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("error")) {
                Toast.makeText(this, "Screen scroll nahi ho saki. Page dobara load karo.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Send the text already present in the active AI composer.
    // Each supported website is handled separately to avoid clicking unrelated controls.
    private void sendCurrentMessage() {
        if (webView == null || !browserOpen) return;
        String script = "(function(){try{"
                + "var host=(location.hostname||'').toLowerCase();"
                + "var isGemini=host==='gemini.google.com';"
                + "var isChatGPT=host==='chatgpt.com'||host==='chat.openai.com';"
                + "if(!isGemini&&!isChatGPT)return 'unsupported';"
                + "var selectors=['textarea#prompt-textarea','rich-textarea [contenteditable]',"
                + "'[role=textbox]','textarea','[contenteditable=true]'];"
                + "var editor=null;"
                + "for(var i=0;i<selectors.length&&!editor;i++){"
                + "var nodes=document.querySelectorAll(selectors[i]);"
                + "for(var j=nodes.length-1;j>=0;j--){var n=nodes[j];"
                + "var r=n.getBoundingClientRect();"
                + "var txt=(n.innerText||n.textContent||n.value||'').trim();"
                + "if(r.width>0&&r.height>0&&!n.disabled&&!n.readOnly&&txt){editor=n;break;}}}"
                + "if(!editor)return 'empty';"
                + "var form=editor.closest('form');"
                + "var buttons=[].slice.call(document.querySelectorAll('button,[role=button]'));"
                + "var best=null,bestScore=0;"
                + "buttons.forEach(function(b){"
                + "var r=b.getBoundingClientRect();"
                + "if(r.width<=0||r.height<=0||b.disabled||b.getAttribute('aria-disabled')==='true')return;"
                + "var label=((b.getAttribute('aria-label')||'')+' '+"
                + "(b.getAttribute('data-testid')||'')+' '+"
                + "(b.getAttribute('title')||'')+' '+"
                + "(b.innerText||b.textContent||'')).toLowerCase();"
                + "var score=0;"
                + "if(label.indexOf('send')>=0)score+=10;"
                + "if(label.indexOf('submit')>=0)score+=8;"
                + "if(label.indexOf('send-button')>=0)score+=12;"
                + "if(isChatGPT&&label.indexOf('prompt')>=0&&label.indexOf('send')>=0)score+=4;"
                + "if(isGemini&&label.indexOf('message')>=0&&label.indexOf('send')>=0)score+=4;"
                + "if(score>bestScore){best=b;bestScore=score;}"
                + "});"
                + "if(best&&bestScore>=8){best.click();return 'sent';}"
                + "if(form&&typeof form.requestSubmit==='function'){form.requestSubmit();return 'submitted';}"
                + "return 'button_missing';"
                + "}catch(e){return 'error';}})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && (result.contains("sent") || result.contains("submitted"))) {
                Toast.makeText(this, "Message AI ko send kar diya.",
                        Toast.LENGTH_SHORT).show();
            } else if (result != null && result.contains("empty")) {
                Toast.makeText(this, "Pehle message box mein text likho ya voice se bolo.",
                        Toast.LENGTH_LONG).show();
            } else if (result != null && result.contains("unsupported")) {
                Toast.makeText(this, "Ye Send control sirf Gemini aur ChatGPT ke liye hai.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Send button detect nahi hua. Message box check karo.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestMicAndActivate() {
        if (webView == null || !browserOpen) return;
        if (hasAudioPermission()) {
            startNativeSpeechInput();
        } else if (Build.VERSION.SDK_INT >= 23) {
            pendingMic = true;
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            Toast.makeText(this, "Microphone permission is not available.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Native Android speech-to-text: the website no longer has to transcribe
    // its own audio stream. The recognized words are inserted into its composer.
    private void startNativeSpeechInput() {
        if (webView == null || !browserOpen) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,
                    "Is TV par Android Speech Recognition service available nahi hai.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            stopNativeSpeechInput();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    nativeSpeechListening = true;
                    Toast.makeText(MainActivity.this, "Listening... bolo ab.",
                            Toast.LENGTH_SHORT).show();
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    nativeSpeechListening = false;
                    Toast.makeText(MainActivity.this, "Transcribing...",
                            Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(int error) {
                    nativeSpeechListening = false;
                    stopNativeSpeechInput();
                    String message = error == SpeechRecognizer.ERROR_NO_MATCH
                            ? "Awaaz samajh nahi aayi. Dobara try karo."
                            : error == SpeechRecognizer.ERROR_NETWORK
                            || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                            ? "Speech service ka network issue hai."
                            : error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                            ? "Android microphone permission check karo."
                            : "Voice recognition fail hui. Speech service check karo.";
                    Toast.makeText(MainActivity.this, message,
                            Toast.LENGTH_LONG).show();
                }
                @Override public void onResults(Bundle results) {
                    nativeSpeechListening = false;
                    ArrayList<String> matches = results == null ? null
                            : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String transcript = matches == null || matches.isEmpty()
                            ? "" : matches.get(0);
                    stopNativeSpeechInput();
                    if (transcript.trim().isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                "Koi text recognize nahi hua. Dobara bolo.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        insertTranscriptIntoComposer(transcript.trim());
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speechRecognizer.startListening(intent);
        } catch (Throwable error) {
            stopNativeSpeechInput();
            Toast.makeText(this,
                    "Native voice service start nahi ho saki. TV par Google Speech Services check karo.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void insertTranscriptIntoComposer(String transcript) {
        if (webView == null || !browserOpen) return;
        final String quoted;
        try {
            quoted = JSONObject.quote(transcript);
        } catch (Throwable error) {
            Toast.makeText(this, "Transcript prepare nahi ho saka.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String script = "(function(){try{"
                + "var text=" + quoted + ";"
                + "var selectors=['textarea#prompt-textarea',"
                + " 'rich-textarea [contenteditable]',"
                + " '[role=textbox]','textarea','input','[contenteditable]'];"
                + "var el=null;"
                + "for(var i=0;i<selectors.length&&!el;i++){"
                + "var list=document.querySelectorAll(selectors[i]);"
                + "for(var j=list.length-1;j>=0;j--){var n=list[j];"
                + "var r=n.getBoundingClientRect();"
                + "if(r.width>0&&r.height>0&&!n.disabled&&!n.readOnly){el=n;break;}}}"
                + "if(!el)return 'missing';"
                + "el.focus();"
                + "var isEdit=el.isContentEditable||el.getAttribute('contenteditable')==='true';"
                + "if(isEdit){"
                + "var old=el.innerText||el.textContent||'';"
                + "el.textContent=(old?old+' ':'')+text;"
                + "try{var range=document.createRange();range.selectNodeContents(el);"
                + "range.collapse(false);var sel=window.getSelection();"
                + "sel.removeAllRanges();sel.addRange(range);}catch(e){}"
                + "}else{"
                + "var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var setter=Object.getOwnPropertyDescriptor(proto,'value').set;"
                + "setter.call(el,(el.value?el.value+' ':'')+text);"
                + "}"
                + "el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return 'ok';}catch(e){return 'error';}})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("ok")) {
                Toast.makeText(this, "Text message box mein aa gaya. Ab Send dabao.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        "Voice text mila, lekin website ka message box nahi mila. Page reload karke try karo.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void stopNativeSpeechInput() {
        nativeSpeechListening = false;
        SpeechRecognizer recognizer = speechRecognizer;
        speechRecognizer = null;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Throwable ignored) {}
            try { recognizer.destroy(); } catch (Throwable ignored) {}
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

            if (pendingWebPermissionRequest != null) {
                if (granted) {
                    grantPendingWebsitePermission();
                } else {
                    denyPendingWebsitePermission();
                    Toast.makeText(this, "Android microphone permission was denied.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            if (granted && pendingMic) {
                startNativeSpeechInput();
            } else if (!granted && pendingMic) {
                Toast.makeText(this, "Microphone permission was not granted.",
                        Toast.LENGTH_SHORT).show();
            }
            pendingMic = false;
        }
    }

    private void handleWebsitePermissionRequest(PermissionRequest request) {
        if (request == null) return;

        boolean asksAudio = false;
        boolean asksOther = false;
        String[] resources = request.getResources();
        if (resources != null) {
            for (String resource : resources) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    asksAudio = true;
                } else {
                    asksOther = true;
                }
            }
        }

        String siteKey = trustedWebsiteKey(request.getOrigin());
        if (!asksAudio || asksOther || siteKey == null) {
            request.deny();
            Toast.makeText(this, "Microphone request from an unsupported website was blocked.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (pendingWebPermissionRequest != null
                && pendingWebPermissionRequest != request) {
            denyPendingWebsitePermission();
        }

        pendingWebPermissionRequest = request;
        pendingWebsiteKey = siteKey;

        if (isWebsiteMicAllowed(siteKey)) {
            if (hasAudioPermission()) {
                grantPendingWebsitePermission();
            } else {
                requestAndroidMicForWebsite();
            }
            return;
        }

        String siteName = websiteDisplayName(siteKey);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Allow microphone?")
                .setMessage(siteName + " is asking to use the microphone. "
                        + "You can allow it once or remember this choice in SITE settings.")
                .setPositiveButton("Allow once", (d, which) ->
                        allowPendingWebsitePermission(false))
                .setNeutralButton("Always allow", (d, which) ->
                        allowPendingWebsitePermission(true))
                .setNegativeButton("Block", (d, which) ->
                        denyPendingWebsitePermission())
                .create();
        pendingPermissionDialog = dialog;
        dialog.setOnCancelListener(d -> denyPendingWebsitePermission());
        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
            }
        });
        dialog.show();
    }

    private void allowPendingWebsitePermission(boolean remember) {
        if (pendingWebPermissionRequest == null) return;
        rememberPendingWebsitePermission = remember;
        if (remember && pendingWebsiteKey != null) {
            getPreferences(MODE_PRIVATE).edit()
                    .putBoolean(websitePreferenceKey(pendingWebsiteKey), true)
                    .apply();
        }
        dismissPendingPermissionDialog();
        if (hasAudioPermission()) {
            grantPendingWebsitePermission();
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            grantPendingWebsitePermission();
        }
    }

    private void requestAndroidMicForWebsite() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            grantPendingWebsitePermission();
        }
    }

    private void grantPendingWebsitePermission() {
        PermissionRequest request = pendingWebPermissionRequest;
        if (request == null) return;
        pendingWebPermissionRequest = null;
        pendingWebsiteKey = null;
        rememberPendingWebsitePermission = false;
        dismissPendingPermissionDialog();
        try {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            Toast.makeText(this, "Website microphone permission allowed.",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            try { request.deny(); } catch (Throwable ignored) {}
            Toast.makeText(this, "Web microphone access could not be started.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void denyPendingWebsitePermission() {
        PermissionRequest request = pendingWebPermissionRequest;
        pendingWebPermissionRequest = null;
        pendingWebsiteKey = null;
        rememberPendingWebsitePermission = false;
        dismissPendingPermissionDialog();
        if (request != null) {
            try { request.deny(); } catch (Throwable ignored) {}
        }
    }

    private void dismissPendingPermissionDialog() {
        AlertDialog dialog = pendingPermissionDialog;
        pendingPermissionDialog = null;
        if (dialog != null && dialog.isShowing()) {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
        }
    }

    private String trustedWebsiteKey(Uri origin) {
        if (origin == null || !"https".equalsIgnoreCase(origin.getScheme())) return null;
        String host = origin.getHost();
        if (host == null) return null;
        host = host.toLowerCase(java.util.Locale.ROOT);
        if ("gemini.google.com".equals(host)) return "gemini";
        if ("chatgpt.com".equals(host) || "chat.openai.com".equals(host)) {
            return "chatgpt";
        }
        return null;
    }

    private String currentWebsiteKey() {
        if (webView == null || webView.getUrl() == null) return null;
        try {
            return trustedWebsiteKey(Uri.parse(webView.getUrl()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String websitePreferenceKey(String siteKey) {
        return "website_mic_" + siteKey;
    }

    private boolean isWebsiteMicAllowed(String siteKey) {
        return getPreferences(MODE_PRIVATE)
                .getBoolean(websitePreferenceKey(siteKey), false);
    }

    private String websiteDisplayName(String siteKey) {
        return "gemini".equals(siteKey) ? "Google Gemini" : "ChatGPT";
    }

    private void openWebsitePermissionSettings() {
        String siteKey = currentWebsiteKey();
        if (siteKey == null) {
            Toast.makeText(this, "Open Gemini or ChatGPT first to manage its mic permission.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        boolean allowed = isWebsiteMicAllowed(siteKey);
        String status = allowed ? "ALLOWED" : "BLOCKED";
        String appStatus = hasAudioPermission() ? "Android app permission: ON"
                : "Android app permission: OFF";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(websiteDisplayName(siteKey) + " microphone")
                .setMessage("Website permission: " + status + "\n" + appStatus
                        + "\n\nThis setting controls microphone access for this website inside SAJI AI TV.")
                .setPositiveButton(allowed ? "Keep allowed" : "Allow microphone",
                        (d, which) -> {
                            getPreferences(MODE_PRIVATE).edit()
                                    .putBoolean(websitePreferenceKey(siteKey), true)
                                    .apply();
                            Toast.makeText(this, websiteDisplayName(siteKey)
                                    + " microphone is allowed.", Toast.LENGTH_SHORT).show();
                        })
                .setNegativeButton("Block microphone", (d, which) -> {
                    getPreferences(MODE_PRIVATE).edit()
                            .putBoolean(websitePreferenceKey(siteKey), false)
                            .apply();
                    if (siteKey.equals(pendingWebsiteKey)) {
                        denyPendingWebsitePermission();
                    }
                    Toast.makeText(this, websiteDisplayName(siteKey)
                            + " microphone is blocked.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
            }
        });
        dialog.show();
    }

    // Track website microphone streams so SAJI can release its own stale
    // capture before asking Android/WebView to open a new voice session.
    private void installMicLifecycleScript(WebView view) {
        String script = "(function(){"
                + "if(window.__sajiMicLifecycleReady)return;"
                + "window.__sajiMicLifecycleReady=true;"
                + "window.__sajiMicStreams=[];"
                + "try{var md=navigator.mediaDevices;"
                + "if(md&&md.getUserMedia){"
                + "var original=md.getUserMedia.bind(md);"
                + "md.getUserMedia=function(constraints){"
                + "return original(constraints).then(function(stream){"
                + "window.__sajiMicStreams.push(stream);return stream;"
                + "});};}}catch(e){}"
                + "window.__sajiStopMic=function(){var stopped=0;"
                + "try{(window.__sajiMicStreams||[]).forEach(function(stream){"
                + "stream.getAudioTracks().forEach(function(track){"
                + "try{track.stop();stopped++;}catch(e){}});});"
                + "window.__sajiMicStreams=[];"
                + "}catch(e){}return stopped;};"
                + "})()";
        try {
            view.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    private void stopWebViewMic() {
        try {
            if (webView != null) {
                webView.evaluateJavascript(
                        "try{window.__sajiStopMic&&window.__sajiStopMic();}catch(e){}",
                        null);
            }
        } catch (Throwable ignored) {
        }
    }

    private void activateWebsiteMic() {
        if (webView == null || micActivationPending) return;
        micActivationPending = true;
        final WebView target = webView;

        // Stop the last WebView capture first, then give Android time to release it.
        target.evaluateJavascript(
                "try{window.__sajiStopMic?window.__sajiStopMic():0;}catch(e){0}",
                result -> target.postDelayed(() -> {
                    if (target != webView || !browserOpen) {
                        micActivationPending = false;
                        return;
                    }
                    // Do not block based on AudioManager's global active-session
                    // list: some TV boxes report Assistant/hotword sessions here
                    // even when Android can still route input to this foreground app.
                    // Let WebView and Android's input-priority policy decide.
                    clickWebsiteMic(target);
                }, 450));
    }

    private void clickWebsiteMic(WebView target) {
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
        target.evaluateJavascript(script, result -> {
            micActivationPending = false;
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
        private final int iconMode;
        private final Path path = new Path();
        private boolean focused;

        IconView(Activity activity, int iconMode) {
            super(activity);
            this.iconMode = iconMode;
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

            if (iconMode == 1) {
                RectF capsule = new RectF(18, 5, 30, 27);
                canvas.drawRoundRect(capsule, 6, 6, paint);
                path.reset();
                path.moveTo(12, 22);
                path.cubicTo(12, 31, 17, 36, 24, 36);
                path.cubicTo(31, 36, 36, 31, 36, 22);
                canvas.drawPath(path, paint);
                canvas.drawLine(24, 36, 24, 42, paint);
                canvas.drawLine(17, 42, 31, 42, paint);
            } else if (iconMode == 0) {
                path.reset();
                path.moveTo(27, 10);
                path.lineTo(13, 24);
                path.lineTo(27, 38);
                path.moveTo(14, 24);
                path.lineTo(37, 24);
                canvas.drawPath(path, paint);
            } else if (iconMode == 4) {
                path.reset();
                path.moveTo(10, 29);
                path.lineTo(24, 15);
                path.lineTo(38, 29);
                path.moveTo(24, 16);
                path.lineTo(24, 40);
                canvas.drawPath(path, paint);
            } else if (iconMode == 5) {
                path.reset();
                path.moveTo(10, 19);
                path.lineTo(24, 33);
                path.lineTo(38, 19);
                path.moveTo(24, 8);
                path.lineTo(24, 32);
                canvas.drawPath(path, paint);
            } else if (iconMode == 3) {
                path.reset();
                path.moveTo(7, 23);
                path.lineTo(41, 7);
                path.lineTo(30, 41);
                path.lineTo(23, 27);
                path.lineTo(7, 23);
                path.moveTo(23, 27);
                path.lineTo(41, 7);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawRoundRect(new RectF(10, 10, 38, 38), 5, 5, paint);
                canvas.drawLine(16, 18, 32, 18, paint);
                canvas.drawLine(16, 24, 32, 24, paint);
                canvas.drawLine(16, 30, 32, 30, paint);
                canvas.drawCircle(21, 18, 2.5f, paint);
                canvas.drawCircle(28, 24, 2.5f, paint);
                canvas.drawCircle(23, 30, 2.5f, paint);
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
        stopNativeSpeechInput();
        try {
            if (webView != null) {
                stopWebViewMic();
                webView.onPause();
            }
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopNativeSpeechInput();
        try {
            denyPendingWebsitePermission();
            if (webView != null) {
                stopWebViewMic();
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
