package com.websitewrapper.app;

import android.app.Activity;
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
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String HOME_URL = BuildConfig.HOME_URL;

    private WebView webView;
    private LinearLayout rootLayout;
    private LinearLayout errorLayout;
    private TextView errorText;

    private String allowedHost;
    private boolean mainFrameLoadFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri homeUri = Uri.parse(HOME_URL);
        String host = homeUri.getHost();

        if (host == null || host.isEmpty()
                || !"https".equalsIgnoreCase(homeUri.getScheme())) {
            showInitialError(
                    "Invalid website address.\n\n" + HOME_URL
            );
            return;
        }

        allowedHost = host.toLowerCase(Locale.ROOT);

        if (allowedHost.startsWith("www.")) {
            allowedHost = allowedHost.substring(4);
        }

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

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);

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

        setContentView(rootLayout);
    }

    private void createWebView() {
        webView = new WebView(this);

        rootLayout.removeAllViews();

        rootLayout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        rootLayout.addView(errorLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
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
            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
                // Embedded resources may load from other domains.
                // Only restrict navigation of the main page.
                if (!request.isForMainFrame()) {
                    return false;
                }

                // Main-frame navigation must stay on the original domain
                // or one of its subdomains. Redirects get no exception.
                return !isOriginalSite(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                // Older Android versions do not expose isForMainFrame()
                // in this callback, so apply the domain rule here.
                return !isOriginalSite(Uri.parse(url));
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
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
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    mainFrameLoadFailed = true;

                    showError(
                            "The website could not be loaded.\n\n"
                                    + error.getDescription()
                                    + "\n\nAddress:\n"
                                    + request.getUrl()
                    );
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    mainFrameLoadFailed = true;

                    showError(
                            "The website could not be loaded.\n\n"
                                    + description
                                    + "\n\nAddress:\n"
                                    + failingUrl
                    );
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse response
            ) {
                if (request.isForMainFrame()
                        && response.getStatusCode() >= 400) {
                    mainFrameLoadFailed = true;

                    showError(
                            "The website returned an error.\n\n"
                                    + "HTTP status: "
                                    + response.getStatusCode()
                                    + "\n\nAddress:\n"
                                    + request.getUrl()
                    );
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView view,
                    RenderProcessGoneDetail detail
            ) {
                rootLayout.removeView(view);
                view.destroy();

                createWebView();
                loadHome();

                return true;
            }
        });
    }

    private boolean isOriginalSite(Uri uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }

        // Only HTTPS pages are allowed in the main frame.
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        if (host.startsWith("www.")) {
            host = host.substring(4);
        }

        return host.equals(allowedHost)
                || host.endsWith("." + allowedHost);
    }

    private void loadHome() {
        if (webView != null) {
            mainFrameLoadFailed = false;
            hideError();
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
        if (errorText == null || errorLayout == null || webView == null) {
            return;
        }

        errorText.setText(message);
        webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
    }

    private void showInitialError(String message) {
        TextView messageView = new TextView(this);
        messageView.setTextColor(Color.DKGRAY);
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(32, 32, 32, 32);
        messageView.setText(message);

        setContentView(messageView);
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
