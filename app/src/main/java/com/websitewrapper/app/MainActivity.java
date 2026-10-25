package com.websitewrapper.app;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String HOME_URL = BuildConfig.HOME_URL;

    private WebView webView;
    private LinearLayout errorLayout;
    private TextView errorText;
    private String lastError = "The website could not be loaded.";

    private String allowedHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri homeUri = Uri.parse(HOME_URL);
        String host = homeUri.getHost();

        if (host == null || host.isEmpty()) {
            showInitialError("Invalid website address:\n" + HOME_URL);
            return;
        }

        // Normalize the configured domain.
        allowedHost = host.toLowerCase(Locale.ROOT);
        if (allowedHost.startsWith("www.")) {
            allowedHost = allowedHost.substring(4);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        errorLayout = new LinearLayout(this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setPadding(32, 32, 32, 32);
        errorLayout.setBackgroundColor(Color.WHITE);
        errorLayout.setVisibility(View.GONE);

        errorText = new TextView(this);
        errorText.setTextColor(Color.DKGRAY);
        errorText.setTextSize(16);
        errorText.setGravity(Gravity.CENTER);
        errorLayout.addView(errorText);

        Button retryButton = new Button(this);
        retryButton.setText("Try again");
        retryButton.setOnClickListener(v -> loadHome());
        errorLayout.addView(retryButton);

        root.addView(errorLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);

        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
            CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                // Embedded resources can load from other domains.
                if (!request.isForMainFrame()) {
                    return false;
                }

                // Only allow approved page navigation.
                return !isAllowed(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                return !isAllowed(Uri.parse(url));
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                hideError();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                hideError();
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    String description =
                            error.getDescription().toString();

                    lastError = "Could not load the website.\n\n"
                            + description
                            + "\n\nAddress: "
                            + request.getUrl();

                    showError(lastError);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl
            ) {
                if (Build.VERSION.SDK_INT < 23) {
                    lastError = "Could not load the website.\n\n"
                            + description
                            + "\n\nAddress: "
                            + failingUrl;

                    showError(lastError);
                }
            }
        });

        if (savedInstanceState != null) {
            android.webkit.WebBackForwardList restored =
                    webView.restoreState(savedInstanceState);

            if (restored == null || restored.getSize() == 0) {
                loadHome();
            }
        } else {
            loadHome();
        }
    }

    private boolean isAllowed(Uri uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        // Allow the configured domain and its subdomains.
        // This does not allow lookalike domains such as
        // example.com.evil.com.
        if (host.equals(allowedHost)
                || host.endsWith("." + allowedHost)) {
            return true;
        }

        // Google Apps Script web apps can redirect from
        // script.google.com to script.googleusercontent.com.
        // Allow this redirect only for Apps Script apps.
        return "script.google.com".equals(allowedHost)
                && "script.googleusercontent.com".equals(host);
    }

    private void loadHome() {
        if (webView != null) {
            hideError();
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(HOME_URL);
        }
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
        lastError = message;

        if (errorLayout != null && errorText != null) {
            errorText.setText(
                    "Website could not be opened\n\n" + lastError
            );

            if (webView != null) {
                webView.setVisibility(View.GONE);
            }

            errorLayout.setVisibility(View.VISIBLE);
        }
    }

    private void showInitialError(String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.WHITE);

        TextView messageView = new TextView(this);
        messageView.setTextColor(Color.DKGRAY);
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        messageView.setText(message);

        root.addView(messageView);
        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        if (errorLayout != null
                && errorLayout.getVisibility() == View.VISIBLE) {
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
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
