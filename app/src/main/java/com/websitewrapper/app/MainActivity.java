package com.websitewrapper.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String HOME_URL = BuildConfig.HOME_URL;

    private WebView webView;
    private LinearLayout rootLayout;
    private LinearLayout errorLayout;
    private TextView errorText;
    private Button copyButton;

    private String allowedHost;
    private boolean mainFrameLoadFailed = false;

    // Hosts visited during the current page flow.
    private final Set<String> flowHosts = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri homeUri = Uri.parse(HOME_URL);
        String host = homeUri.getHost();

        if (host == null || host.isEmpty()
                || !"https".equalsIgnoreCase(homeUri.getScheme())) {
            showInitialError("Invalid website address.\n\n" + HOME_URL);
            return;
        }

        allowedHost = normalizeHost(host);
        flowHosts.add(allowedHost);

        buildLayout();
        createWebView();

        if (savedInstanceState != null) {
            Bundle webState = savedInstanceState.getBundle("webview_state");

            if (webState == null || webView.restoreState(webState) == null) {
                loadHome();
            }
        } else {
            loadHome();
        }
    }

    private String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        return normalized;
    }

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);

        errorLayout = new LinearLayout(this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setPadding(24, 24, 24, 24);
        errorLayout.setBackgroundColor(Color.WHITE);
        errorLayout.setVisibility(View.GONE);

        ScrollView scrollView = new ScrollView(this);
        errorText = new TextView(this);
        errorText.setTextColor(Color.DKGRAY);
        errorText.setTextSize(16);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextIsSelectable(true);
        errorText.setText("Website diagnostic information will appear here.");
        scrollView.addView(errorText);

        errorLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        copyButton = new Button(this);
        copyButton.setText("Copy diagnostic details");
        copyButton.setOnClickListener(v -> copyDiagnostics());
        errorLayout.addView(copyButton);

        Button retryButton = new Button(this);
        retryButton.setText("Try again");
        retryButton.setOnClickListener(v -> loadHome());
        errorLayout.addView(retryButton);

        setContentView(rootLayout);
    }

    private void createWebView() {
        webView = new WebView(this);
        rootLayout.removeAllViews();

        rootLayout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));
        rootLayout.addView(errorLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {

                if (!request.isForMainFrame()) {
                    return false;
                }

                return handleMainFrameNavigation(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleMainFrameNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(
                    WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameLoadFailed = false;
                hideError();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mainFrameLoadFailed) {
                    hideError();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameLoadFailed = true;
                    showError(
                            "The website could not be loaded."
                                    + "\n\nError code: " + error.getErrorCode()
                                    + "\nDescription: " + error.getDescription()
                                    + "\n\nFailing address:\n" + request.getUrl()
                                    + "\n\nConfigured website:\n" + HOME_URL
                    );
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    mainFrameLoadFailed = true;
                    showError(
                            "The website could not be loaded."
                                    + "\n\nError code: " + errorCode
                                    + "\nDescription: " + description
                                    + "\n\nFailing address:\n" + failingUrl
                                    + "\n\nConfigured website:\n" + HOME_URL
                    );
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    mainFrameLoadFailed = true;
                    showError(
                            "The website returned an HTTP error."
                                    + "\n\nHTTP status: " + response.getStatusCode()
                                    + "\nReason: " + response.getReasonPhrase()
                                    + "\n\nFailing address:\n" + request.getUrl()
                                    + "\n\nConfigured website:\n" + HOME_URL
                    );
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView view, RenderProcessGoneDetail detail) {
                rootLayout.removeView(view);
                view.destroy();
                webView = null;

                showError(
                        "The website renderer stopped unexpectedly."
                                + "\n\nDid crash: " + detail.didCrash()
                                + "\nRenderer priority at exit: "
                                + detail.rendererPriorityAtExit()
                                + "\n\nConfigured website:\n" + HOME_URL
                                + "\n\nTap Try again to reload."
                );
                return true;
            }
        });
    }

    private boolean handleMainFrameNavigation(Uri target) {
        if (target == null || target.getHost() == null
                || !"https".equalsIgnoreCase(target.getScheme())) {
            showBlockedNavigation(target, "Only HTTPS navigation is allowed.");
            return true;
        }

        String host = normalizeHost(target.getHost());

        // Original website and its subdomains are always permitted.
        if (isOriginalSite(target)) {
            flowHosts.add(host);
            return false;
        }

        /*
         * Allow cross-domain navigation during this page flow.
         * The destination is recorded so subsequent redirects through
         * the same destination can continue. This is not a perfect way
         * to distinguish redirects from user-clicked links.
         */
        flowHosts.add(host);
        return false;
    }

    private boolean isOriginalSite(Uri uri) {
        if (uri == null || uri.getHost() == null
                || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = normalizeHost(uri.getHost());
        return host.equals(allowedHost) || host.endsWith("." + allowedHost);
    }

    private void showBlockedNavigation(Uri target, String reason) {
        mainFrameLoadFailed = true;
        showError(
                "NAVIGATION BLOCKED"
                        + "\n\nReason: " + reason
                        + "\n\nAddress:\n" + target
                        + "\n\nConfigured website:\n" + HOME_URL
        );
    }

    private void loadHome() {
        if (webView == null) {
            createWebView();
        }

        flowHosts.clear();
        flowHosts.add(allowedHost);
        mainFrameLoadFailed = false;
        hideError();
        webView.loadUrl(HOME_URL);
    }

    private void hideError() {
        if (errorLayout != null) {
            errorLayout.setVisibility(View.GONE);
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        if (errorText == null || errorLayout == null) {
            return;
        }

        errorText.setText(message);
        copyButton.setText("Copy diagnostic details");

        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
        errorLayout.setVisibility(View.VISIBLE);
    }

    private void copyDiagnostics() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Website app diagnostics", errorText.getText().toString()));

        copyButton.setText("Copied!");
        Toast.makeText(
                this, "Diagnostic details copied. Paste them into ChatGPT.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void showInitialError(String message) {
        TextView messageView = new TextView(this);
        messageView.setTextColor(Color.DKGRAY);
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(32, 32, 32, 32);
        messageView.setTextIsSelectable(true);
        messageView.setText(message);
        setContentView(messageView);
    }

    @Override
    public void onBackPressed() {
        if (errorLayout != null && errorLayout.getVisibility() == View.VISIBLE) {
            if (webView != null && webView.canGoBack()) {
                hideError();
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            Bundle webState = new Bundle();
            webView.saveState(webState);
            outState.putBundle("webview_state", webState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
