package com.websitewrapper.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
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

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String HOME_URL = BuildConfig.HOME_URL;
    private static final String ALLOWED_HOSTS = BuildConfig.ALLOWED_HOSTS;

    private static final int REQUEST_WEB_PERMISSIONS = 2101;
    private static final int REQUEST_GEOLOCATION = 2102;
    private static final int REQUEST_FILE_CHOOSER = 2103;

    private WebView webView;
    private LinearLayout rootLayout;
    private LinearLayout errorLayout;
    private TextView errorText;
    private Button copyButton;

    private String allowedHost;
    private String lastBlockedUrl = "";
    private boolean mainFrameLoadFailed = false;

    private final Set<String> extraAllowedHosts = new HashSet<>();
    private final Set<String> navigationHistory = new LinkedHashSet<>();

    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraPhotoUri;

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
        loadAllowedHosts();
        recordNavigation(HOME_URL, "START");

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

    private boolean isValidHostname(String host) {
        return host != null && host.matches(
                "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*"
        );
    }

    private void loadAllowedHosts() {
        if (ALLOWED_HOSTS == null || ALLOWED_HOSTS.trim().isEmpty()) {
            return;
        }

        for (String item : ALLOWED_HOSTS.split(",")) {
            String host = normalizeHost(item.trim());
            if (isValidHostname(host)) {
                extraAllowedHosts.add(host);
            }
        }
    }

    private void recordNavigation(String url, String event) {
        if (url == null) return;

        navigationHistory.add(event + " | " + url);
        while (navigationHistory.size() > 60) {
            navigationHistory.remove(navigationHistory.iterator().next());
        }
    }

    private boolean isAllowedOrigin(Uri uri) {
        if (uri == null || uri.getHost() == null
                || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = normalizeHost(uri.getHost());
        return isOriginalSite(uri) || isExplicitlyAllowed(host);
    }

    private boolean isExplicitlyAllowed(String host) {
        for (String allowed : extraAllowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
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

    private String diagnosticHistory() {
        StringBuilder result = new StringBuilder();
        result.append("Configured website:\n").append(HOME_URL);
        result.append("\n\nObserved navigation history:");

        if (navigationHistory.isEmpty()) {
            result.append("\nNo navigation recorded.");
        }

        for (String entry : navigationHistory) {
            result.append("\n").append(entry);
        }

        if (!lastBlockedUrl.isEmpty()) {
            Uri blocked = Uri.parse(lastBlockedUrl);
            result.append("\n\nBlocked destination:\n").append(lastBlockedUrl);

            if (blocked.getHost() != null) {
                result.append("\n\nSuggested custom-rules entry:\n")
                        .append(normalizeHost(blocked.getHost()));
            }
        }

        result.append(
                "\n\nNote: WebView does not expose every internal redirect "
                + "or background-resource failure. This report contains "
                + "observable navigation and page-load errors."
        );

        return result.toString();
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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        rootLayout.addView(errorLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) {
                    pendingWebPermission = null;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                handleGeolocationRequest(origin, callback);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;
                openFileChooser(params);
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition,
                                     mimeType, contentLength) ->
                downloadFile(url, userAgent, contentDisposition, mimeType));

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
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
                recordNavigation(url, "PAGE STARTED");
                mainFrameLoadFailed = false;
                hideError();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                recordNavigation(url, "PAGE FINISHED");
                if (!mainFrameLoadFailed) hideError();
            }

            @Override
            public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameLoadFailed = true;
                    showError("The website could not be loaded."
                            + "\n\nError code: " + error.getErrorCode()
                            + "\nDescription: " + error.getDescription()
                            + "\n\nFailing address:\n" + request.getUrl()
                            + "\n\n" + diagnosticHistory());
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    mainFrameLoadFailed = true;
                    showError("The website could not be loaded."
                            + "\n\nError code: " + errorCode
                            + "\nDescription: " + description
                            + "\n\nFailing address:\n" + failingUrl
                            + "\n\n" + diagnosticHistory());
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request,
                    WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    mainFrameLoadFailed = true;
                    showError("The website returned an HTTP error."
                            + "\n\nHTTP status: " + response.getStatusCode()
                            + "\nReason: " + response.getReasonPhrase()
                            + "\n\nFailing address:\n" + request.getUrl()
                            + "\n\n" + diagnosticHistory());
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView view, RenderProcessGoneDetail detail) {
                rootLayout.removeView(view);
                view.destroy();
                webView = null;

                showError("The website renderer stopped unexpectedly."
                        + "\n\nDid crash: " + detail.didCrash()
                        + "\n\n" + diagnosticHistory()
                        + "\n\nTap Try again to reload.");
                return true;
            }
        });
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isAllowedOrigin(request.getOrigin())) {
            request.deny();
            recordNavigation(String.valueOf(request.getOrigin()),
                    "DENIED: website permission origin");
            return;
        }

        List<String> requested = new ArrayList<>();

        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                requested.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                requested.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        if (requested.isEmpty()) {
            request.deny();
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String permission : requested) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) {
            grantAllowedWebResources(request);
            return;
        }

        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
        }

        pendingWebPermission = request;
        requestPermissions(missing.toArray(new String[0]), REQUEST_WEB_PERMISSIONS);
    }

    private void grantAllowedWebResources(PermissionRequest request) {
        List<String> granted = new ArrayList<>();

        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            }
        }

        if (granted.isEmpty()) {
            request.deny();
        } else {
            request.grant(granted.toArray(new String[0]));
        }
    }

    private void handleGeolocationRequest(
            String origin, GeolocationPermissions.Callback callback) {
        Uri uri = Uri.parse(origin);

        if (!isAllowedOrigin(uri)) {
            callback.invoke(origin, false, false);
            return;
        }

        boolean fine = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            callback.invoke(origin, true, false);
        } else {
            if (pendingGeoCallback != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
            }

            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;

            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_GEOLOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_WEB_PERMISSIONS) {
            PermissionRequest request = pendingWebPermission;
            pendingWebPermission = null;

            if (request != null) {
                if (isAllowedOrigin(request.getOrigin())) {
                    grantAllowedWebResources(request);
                } else {
                    request.deny();
                }
            }
        } else if (requestCode == REQUEST_GEOLOCATION) {
            if (pendingGeoCallback != null) {
                boolean granted =
                        ContextCompat.checkSelfPermission(
                                this, Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(
                                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;

                pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
        }
    }

    /*
     * Opens the website's file picker and adds a camera option.
     * Keeps the MIME type and accepted file types requested by the website.
     */
    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        Intent contentIntent;

        try {
            contentIntent = params.createIntent();
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        } catch (Exception e) {
            contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentIntent.setType("*/*");
        }

        // A camera option is relevant for image uploads.
        boolean acceptsImages = acceptsImages(params);
        Intent cameraIntent = acceptsImages ? createCameraIntent() : null;

        try {
            if (cameraIntent != null) {
                Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                chooser.putExtra(Intent.EXTRA_INTENT, contentIntent);
                chooser.putExtra(Intent.EXTRA_TITLE,
                        "Choose a file or take a picture");
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                        new Intent[]{cameraIntent});

                startActivityForResult(chooser, REQUEST_FILE_CHOOSER);
            } else {
                startActivityForResult(contentIntent, REQUEST_FILE_CHOOSER);
            }
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("*/*");
                startActivityForResult(fallback, REQUEST_FILE_CHOOSER);
            } catch (ActivityNotFoundException ignored) {
                finishFileChooser(null);
                cameraPhotoUri = null;
                Toast.makeText(this, "No file picker is available.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        if (params == null) return true;

        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;

        boolean hasRecognizedType = false;

        for (String type : types) {
            if (type == null || type.trim().isEmpty()) continue;

            for (String part : type.split(",")) {
                String mime = part.trim().toLowerCase(Locale.ROOT);

                if (mime.isEmpty()) continue;
                hasRecognizedType = true;

                if (mime.equals("*/*")
                        || mime.equals("image/*")
                        || mime.startsWith("image/")) {
                    return true;
                }
            }
        }

        // If the website supplies no usable MIME types, keep camera available.
        return !hasRecognizedType;
    }

    private Intent createCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (intent.resolveActivity(getPackageManager()) == null) {
            cameraPhotoUri = null;
            return null;
        }

        try {
            File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

            if (directory == null) {
                cameraPhotoUri = null;
                return null;
            }

            if (!directory.exists() && !directory.mkdirs()) {
                cameraPhotoUri = null;
                return null;
            }

            File photo = File.createTempFile(
                    "website_photo_", ".jpg", directory);

            cameraPhotoUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            intent.setClipData(ClipData.newRawUri("camera_photo", cameraPhotoUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Some camera apps need an explicit URI grant.
            List<android.content.pm.ResolveInfo> cameraActivities =
                    getPackageManager().queryIntentActivities(
                            intent, PackageManager.MATCH_DEFAULT_ONLY);

            for (android.content.pm.ResolveInfo info : cameraActivities) {
                if (info.activityInfo != null) {
                    grantUriPermission(
                            info.activityInfo.packageName,
                            cameraPhotoUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            }

            return intent;

        } catch (IOException | IllegalArgumentException e) {
            cameraPhotoUri = null;
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE_CHOOSER) {
            Uri[] results = null;

            if (resultCode == RESULT_OK) {
                Uri selectedUri = null;

                if (data != null && data.getData() != null) {
                    selectedUri = data.getData();
                } else if (cameraPhotoUri != null) {
                    // Most camera apps return no data URI when EXTRA_OUTPUT is used.
                    selectedUri = cameraPhotoUri;
                }

                if (selectedUri != null) {
                    results = new Uri[]{selectedUri};
                }
            }

            finishFileChooser(results);

            if (cameraPhotoUri != null) {
                try {
                    revokeUriPermission(
                            cameraPhotoUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                    // The temporary URI grant may already have been revoked.
                }
            }

            cameraPhotoUri = null;
        }
    }

    private void finishFileChooser(Uri[] results) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void downloadFile(
            String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            Uri uri = Uri.parse(url);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !isAllowedOrigin(uri)) {
                Toast.makeText(this,
                        "Download blocked: hostname is not allowed.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Website download");
            request.setDescription("Downloading file");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "website-download-" + System.currentTimeMillis()
                            + extensionForMimeType(mimeType));

            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }

            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

            if (manager == null) {
                throw new IllegalStateException("Download service unavailable");
            }

            manager.enqueue(request);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Couldn't start download: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String extensionForMimeType(String mimeType) {
        if (mimeType == null) return "";

        String extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType);

        return extension == null ? "" : "." + extension;
    }

    private boolean handleMainFrameNavigation(Uri target) {
        if (target == null || target.getHost() == null
                || !"https".equalsIgnoreCase(target.getScheme())) {
            recordNavigation(String.valueOf(target), "BLOCKED: HTTPS required");
            showBlockedNavigation(target, "Only HTTPS navigation is allowed.");
            return true;
        }

        String host = normalizeHost(target.getHost());
        recordNavigation(target.toString(), "NAVIGATION REQUEST");

        if (isOriginalSite(target) || isExplicitlyAllowed(host)) {
            recordNavigation(target.toString(), "ALLOWED");
            return false;
        }

        recordNavigation(target.toString(), "BLOCKED: hostname not allowed");
        showBlockedNavigation(target,
                "This hostname is not in the original website or custom allowlist.");
        return true;
    }

    private void showBlockedNavigation(Uri target, String reason) {
        mainFrameLoadFailed = true;
        lastBlockedUrl = target == null ? "unknown" : target.toString();

        showError("NAVIGATION BLOCKED"
                + "\n\nReason: " + reason
                + "\n\nAddress:\n" + target
                + "\n\n" + diagnosticHistory());
    }

    private void loadHome() {
        if (webView == null) createWebView();

        navigationHistory.clear();
        lastBlockedUrl = "";
        recordNavigation(HOME_URL, "RETRY / START");

        mainFrameLoadFailed = false;
        hideError();
        webView.loadUrl(HOME_URL);
    }

    private void hideError() {
        if (errorLayout != null) errorLayout.setVisibility(View.GONE);
        if (webView != null) webView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        if (errorText == null || errorLayout == null) return;

        errorText.setText(message);
        copyButton.setText("Copy diagnostic details");

        if (webView != null) webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
    }

    private void copyDiagnostics() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Website app diagnostics", errorText.getText().toString()));

        copyButton.setText("Copied!");
        Toast.makeText(this,
                "Diagnostic details copied. Paste them into ChatGPT.",
                Toast.LENGTH_LONG).show();
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
        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
            pendingWebPermission = null;
        }

        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
            pendingGeoCallback = null;
        }

        finishFileChooser(null);

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
