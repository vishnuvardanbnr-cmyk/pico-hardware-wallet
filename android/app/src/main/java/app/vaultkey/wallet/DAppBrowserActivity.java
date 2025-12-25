package app.vaultkey.wallet;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.HashSet;
import java.math.BigInteger;
import java.math.BigDecimal;

public class DAppBrowserActivity extends AppCompatActivity {
    private static final String TAG = "DAppBrowserActivity";
    
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_CHAIN_ID = "chainId";
    
    public static final String ACTION_WEB3_REQUEST = "app.vaultkey.wallet.WEB3_REQUEST";
    public static final String ACTION_WEB3_RESPONSE = "app.vaultkey.wallet.WEB3_RESPONSE";
    public static final String ACTION_BROWSER_EVENT = "app.vaultkey.wallet.BROWSER_EVENT";
    public static final String ACTION_CLOSE_BROWSER = "app.vaultkey.wallet.CLOSE_BROWSER";
    public static final String ACTION_UPDATE_ACCOUNT = "app.vaultkey.wallet.UPDATE_ACCOUNT";
    public static final String ACTION_RESUME_BROWSER = "app.vaultkey.wallet.RESUME_BROWSER";
    public static final String ACTION_REQUEST_PIN = "app.vaultkey.wallet.REQUEST_PIN";
    public static final String ACTION_PIN_RESPONSE = "app.vaultkey.wallet.PIN_RESPONSE";
    
    private WebView webView;
    private ProgressBar progressBar;
    private EditText urlInput;
    private Button backButton;
    private Button forwardButton;
    private Button refreshButton;
    private Button closeButton;
    
    private String currentAddress = "";
    private int currentChainId = 1;
    private String rpcUrl = "https://eth.llamarpc.com";
    
    private BroadcastReceiver responseReceiver;
    private BroadcastReceiver closeReceiver;
    private BroadcastReceiver updateReceiver;
    private BroadcastReceiver resumeReceiver;
    private BroadcastReceiver pinRequestReceiver;
    
    private AlertDialog currentConfirmDialog;
    private AlertDialog currentPinDialog;
    private int pendingRequestId = -1;
    private String pendingMethod = "";
    private String pendingParams = "";
    private String enteredPin = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String url = getIntent().getStringExtra(EXTRA_URL);
        currentAddress = getIntent().getStringExtra(EXTRA_ADDRESS);
        currentChainId = getIntent().getIntExtra(EXTRA_CHAIN_ID, 1);
        rpcUrl = getRpcUrl(currentChainId);
        
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        
        Log.d(TAG, "Opening browser - URL: " + url + ", Address: " + currentAddress + ", ChainId: " + currentChainId);
        
        createUI();
        setupBroadcastReceivers();
        loadUrl(url);
        
        sendBrowserEvent(url, true);
    }
    
    private void createUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        float density = getResources().getDisplayMetrics().density;
        
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#1a1a2e"));
        header.setPadding((int)(8 * density), (int)(12 * density), (int)(8 * density), (int)(12 * density));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        header.setLayoutParams(headerParams);
        
        closeButton = createTextButton("\u2190", density);
        closeButton.setOnClickListener(v -> finish());
        header.addView(closeButton);
        
        urlInput = new EditText(this);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.parseColor("#888888"));
        urlInput.setHint("Enter URL...");
        urlInput.setBackgroundColor(Color.parseColor("#2d2d44"));
        urlInput.setPadding((int)(12 * density), (int)(8 * density), (int)(12 * density), (int)(8 * density));
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToUrl();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        urlParams.setMargins((int)(8 * density), 0, (int)(8 * density), 0);
        urlInput.setLayoutParams(urlParams);
        header.addView(urlInput);
        
        backButton = createTextButton("\u25C0", density);
        backButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            }
        });
        header.addView(backButton);
        
        forwardButton = createTextButton("\u25B6", density);
        forwardButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoForward()) {
                webView.goForward();
            }
        });
        header.addView(forwardButton);
        
        refreshButton = createTextButton("\u21BB", density);
        refreshButton.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
            }
        });
        header.addView(refreshButton);
        
        root.addView(header);
        
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int)(3 * density)
        ));
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar);
        
        createWebView();
        LinearLayout.LayoutParams webViewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        webView.setLayoutParams(webViewParams);
        root.addView(webView);
        
        setContentView(root);
    }
    
    private Button createTextButton(String text, float density) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setPadding((int)(8 * density), (int)(4 * density), (int)(8 * density), (int)(4 * density));
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            (int)(40 * density),
            (int)(40 * density)
        );
        btn.setLayoutParams(params);
        
        return btn;
    }
    
    private void navigateToUrl() {
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            loadUrl(url);
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setGeolocationEnabled(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            try {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF);
            } catch (Exception e) {
                Log.e(TAG, "Force dark error", e);
            }
        }
        
        String ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 VaultKey/1.0";
        settings.setUserAgentString(ua);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        
        webView.addJavascriptInterface(new WalletBridge(), "VaultKeyNative");
        
        String injectionScript = buildInjectionScript();
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                HashSet<String> origins = new HashSet<>();
                origins.add("*");
                WebViewCompat.addDocumentStartJavaScript(webView, injectionScript, origins);
            } catch (Exception e) {
                Log.e(TAG, "Document-start injection failed", e);
            }
        }
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                urlInput.setText(url);
                
                try {
                    view.evaluateJavascript(injectionScript, null);
                } catch (Exception e) {
                    Log.e(TAG, "Injection error", e);
                }
                
                sendBrowserEvent(url, true);
                updateNavigationButtons();
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                urlInput.setText(url);
                
                try {
                    view.evaluateJavascript(injectionScript, null);
                } catch (Exception e) {
                    Log.e(TAG, "Injection error", e);
                }
                
                sendBrowserEvent(url, false);
                updateNavigationButtons();
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open: " + reqUrl);
                }
                return true;
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }
    
    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setAlpha(webView.canGoBack() ? 1.0f : 0.3f);
        }
        if (forwardButton != null) {
            forwardButton.setAlpha(webView.canGoForward() ? 1.0f : 0.3f);
        }
    }
    
    private void loadUrl(String url) {
        if (webView != null) {
            urlInput.setText(url);
            webView.loadUrl(url);
        }
    }
    
    private void setupBroadcastReceivers() {
        responseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int id = intent.getIntExtra("id", 0);
                String result = intent.getStringExtra("result");
                String error = intent.getStringExtra("error");
                handleWeb3Response(id, result, error);
            }
        };
        
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                currentAddress = intent.getStringExtra("address");
                currentChainId = intent.getIntExtra("chainId", currentChainId);
                rpcUrl = getRpcUrl(currentChainId);
                updateWebViewAccount();
            }
        };
        
        resumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                bringToForeground();
            }
        };
        
        pinRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String walletGroupId = intent.getStringExtra("walletGroupId");
                Log.d(TAG, "PIN request received for wallet group: " + walletGroupId);
                runOnUiThread(() -> showPinDialog(walletGroupId));
            }
        };
        
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(responseReceiver, new IntentFilter(ACTION_WEB3_RESPONSE));
        lbm.registerReceiver(closeReceiver, new IntentFilter(ACTION_CLOSE_BROWSER));
        lbm.registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_ACCOUNT));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(ACTION_RESUME_BROWSER));
        lbm.registerReceiver(pinRequestReceiver, new IntentFilter(ACTION_REQUEST_PIN));
    }
    
    private void showPinDialog(String walletGroupId) {
        Log.d(TAG, "showPinDialog called");
        
        if (currentPinDialog != null && currentPinDialog.isShowing()) {
            currentPinDialog.dismiss();
        }
        
        enteredPin = "";
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(20));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        
        TextView titleView = new TextView(this);
        titleView.setText("Enter PIN");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView);
        
        TextView subtitleView = new TextView(this);
        subtitleView.setText("Enter your PIN to sign this transaction");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTextColor(Color.parseColor("#888888"));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(8), 0, dp(24));
        layout.addView(subtitleView);
        
        final TextView pinDotsView = new TextView(this);
        pinDotsView.setText("\u25CB \u25CB \u25CB \u25CB");
        pinDotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        pinDotsView.setTextColor(Color.WHITE);
        pinDotsView.setGravity(Gravity.CENTER);
        pinDotsView.setLetterSpacing(0.3f);
        pinDotsView.setPadding(0, 0, 0, dp(24));
        layout.addView(pinDotsView);
        
        LinearLayout numPad = new LinearLayout(this);
        numPad.setOrientation(LinearLayout.VERTICAL);
        numPad.setGravity(Gravity.CENTER);
        
        String[][] buttons = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"", "0", "\u232B"}
        };
        
        for (String[] row : buttons) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            
            for (String btn : row) {
                Button numBtn = new Button(this);
                numBtn.setText(btn);
                numBtn.setTextColor(Color.WHITE);
                numBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                numBtn.setAllCaps(false);
                
                GradientDrawable btnBg = new GradientDrawable();
                if (btn.isEmpty()) {
                    btnBg.setColor(Color.TRANSPARENT);
                } else {
                    btnBg.setColor(Color.parseColor("#333355"));
                }
                btnBg.setCornerRadius(dp(35));
                numBtn.setBackground(btnBg);
                
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dp(70), dp(70));
                btnParams.setMargins(dp(6), dp(6), dp(6), dp(6));
                numBtn.setLayoutParams(btnParams);
                
                if (!btn.isEmpty()) {
                    numBtn.setOnClickListener(v -> {
                        if (btn.equals("\u232B")) {
                            if (enteredPin.length() > 0) {
                                enteredPin = enteredPin.substring(0, enteredPin.length() - 1);
                            }
                        } else {
                            if (enteredPin.length() < 6) {
                                enteredPin += btn;
                            }
                        }
                        updatePinDots(pinDotsView);
                        
                        if (enteredPin.length() >= 4) {
                            pinDotsView.postDelayed(() -> {
                                if (enteredPin.length() >= 4 && enteredPin.length() <= 6) {
                                    submitPin(walletGroupId);
                                }
                            }, 200);
                        }
                    });
                }
                
                rowLayout.addView(numBtn);
            }
            numPad.addView(rowLayout);
        }
        layout.addView(numPad);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setAllCaps(false);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.parseColor("#555555"));
        cancelBg.setCornerRadius(dp(8));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(dp(48), dp(14), dp(48), dp(14));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.setMargins(0, dp(20), 0, 0);
        cancelBtn.setLayoutParams(cancelParams);
        cancelBtn.setOnClickListener(v -> {
            if (currentPinDialog != null) {
                currentPinDialog.dismiss();
                currentPinDialog = null;
            }
            Intent intent = new Intent(ACTION_PIN_RESPONSE);
            intent.putExtra("pin", "");
            intent.putExtra("cancelled", true);
            LocalBroadcastManager.getInstance(DAppBrowserActivity.this).sendBroadcast(intent);
        });
        layout.addView(cancelBtn);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
        builder.setView(layout);
        builder.setCancelable(false);
        
        currentPinDialog = builder.create();
        if (currentPinDialog.getWindow() != null) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(Color.parseColor("#1A1A2E"));
            dialogBg.setCornerRadius(dp(20));
            currentPinDialog.getWindow().setBackgroundDrawable(dialogBg);
        }
        currentPinDialog.show();
        Log.d(TAG, "PIN dialog shown");
    }
    
    private void updatePinDots(TextView pinDotsView) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < enteredPin.length()) {
                dots.append("\u25CF ");
            } else {
                dots.append("\u25CB ");
            }
        }
        if (enteredPin.length() > 4) {
            for (int i = 4; i < enteredPin.length(); i++) {
                dots.append("\u25CF ");
            }
        }
        pinDotsView.setText(dots.toString().trim());
    }
    
    private void submitPin(String walletGroupId) {
        Log.d(TAG, "Submitting PIN");
        if (currentPinDialog != null) {
            currentPinDialog.dismiss();
            currentPinDialog = null;
        }
        
        Intent intent = new Intent(ACTION_PIN_RESPONSE);
        intent.putExtra("pin", enteredPin);
        intent.putExtra("walletGroupId", walletGroupId);
        intent.putExtra("cancelled", false);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        enteredPin = "";
    }
    
    private void bringToForeground() {
        try {
            Intent intent = new Intent(this, DAppBrowserActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error bringing browser to foreground", e);
        }
    }
    
    private void handleWeb3Response(int id, String result, String error) {
        String script;
        if (error != null && !error.isEmpty()) {
            String escapedError = error.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "");
            script = "(function(){" +
                "if(window._vkCallbacks&&window._vkCallbacks[" + id + "]){" +
                "window._vkCallbacks[" + id + "].reject(new Error('" + escapedError + "'));" +
                "delete window._vkCallbacks[" + id + "];" +
                "}})();";
        } else {
            script = "(function(){" +
                "if(window._vkCallbacks&&window._vkCallbacks[" + id + "]){" +
                "window._vkCallbacks[" + id + "].resolve(" + result + ");" +
                "delete window._vkCallbacks[" + id + "];" +
                "}})();";
        }
        
        if (webView != null) {
            runOnUiThread(() -> {
                try {
                    webView.evaluateJavascript(script, null);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending response", e);
                }
            });
        }
    }
    
    private void updateWebViewAccount() {
        if (webView != null) {
            String hexChainId = "0x" + Integer.toHexString(currentChainId);
            String script = 
                "(function(){" +
                "if(window.__vkUpdate){" +
                "window.__vkUpdate('" + currentAddress + "','" + hexChainId + "','" + rpcUrl + "');" +
                "}else if(window.ethereum){" +
                "window.ethereum.selectedAddress='" + currentAddress + "';" +
                "window.ethereum.chainId='" + hexChainId + "';" +
                "if(window.ethereum.emit){" +
                "window.ethereum.emit('accountsChanged',['" + currentAddress + "']);" +
                "window.ethereum.emit('chainChanged','" + hexChainId + "');" +
                "}}})();";
            
            runOnUiThread(() -> {
                try {
                    webView.evaluateJavascript(script, null);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating account", e);
                }
            });
        }
    }
    
    private void sendBrowserEvent(String url, boolean loading) {
        Intent intent = new Intent(ACTION_BROWSER_EVENT);
        intent.putExtra("url", url);
        intent.putExtra("loading", loading);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendWeb3Request(int id, String method, String params) {
        if (method.equals("eth_sendTransaction") || 
            method.equals("eth_signTransaction") ||
            method.equals("personal_sign") ||
            method.equals("eth_sign") ||
            method.startsWith("eth_signTypedData")) {
            pendingRequestId = id;
            pendingMethod = method;
            pendingParams = params;
            
            runOnUiThread(() -> showConfirmationDialog(id, method, params));
        } else {
            Intent intent = new Intent(ACTION_WEB3_REQUEST);
            intent.putExtra("id", id);
            intent.putExtra("method", method);
            intent.putExtra("params", params);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
    
    private void showConfirmationDialog(int id, String method, String params) {
        if (currentConfirmDialog != null && currentConfirmDialog.isShowing()) {
            currentConfirmDialog.dismiss();
        }
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(16));
        scrollView.addView(layout);
        
        String title = "Confirm Transaction";
        String actionType = "Contract Interaction";
        String toAddress = "";
        String valueAmount = "0";
        String gasEstimate = "";
        String dataHex = "";
        String functionName = "";
        boolean hasData = false;
        
        try {
            org.json.JSONArray paramsArray = new org.json.JSONArray(params);
            
            if (method.equals("eth_sendTransaction") || method.equals("eth_signTransaction")) {
                if (paramsArray.length() > 0) {
                    org.json.JSONObject tx = paramsArray.getJSONObject(0);
                    toAddress = tx.optString("to", "");
                    String valueHex = tx.optString("value", "0x0");
                    dataHex = tx.optString("data", "0x");
                    String gasHex = tx.optString("gas", tx.optString("gasLimit", "0x0"));
                    
                    valueAmount = formatWeiToEth(valueHex);
                    gasEstimate = formatGas(gasHex);
                    hasData = dataHex.length() > 2;
                    
                    if (toAddress.isEmpty()) {
                        actionType = "Contract Deployment";
                        title = "Deploy Contract";
                    } else if (!hasData) {
                        actionType = "Send " + getChainSymbol();
                        title = "Send " + getChainSymbol();
                    } else {
                        functionName = decodeFunction(dataHex);
                        if (functionName.startsWith("approve")) {
                            actionType = "Token Approval";
                            title = "Approve Token";
                        } else if (functionName.startsWith("transfer")) {
                            actionType = "Token Transfer";
                            title = "Transfer Token";
                        } else if (functionName.contains("swap") || functionName.contains("Swap")) {
                            actionType = "Swap";
                            title = "Swap Tokens";
                        } else {
                            actionType = "Contract Interaction";
                            title = "Confirm Transaction";
                        }
                    }
                }
            } else if (method.equals("personal_sign") || method.equals("eth_sign")) {
                title = "Sign Message";
                actionType = "Message Signature";
                if (paramsArray.length() > 0) {
                    String message = paramsArray.getString(0);
                    if (message.startsWith("0x")) {
                        try {
                            byte[] bytes = hexStringToByteArray(message.substring(2));
                            dataHex = new String(bytes, "UTF-8");
                        } catch (Exception e) {
                            dataHex = message;
                        }
                    } else {
                        dataHex = message;
                    }
                }
            } else if (method.contains("signTypedData")) {
                title = "Sign Typed Data";
                actionType = "Typed Data Signature";
                if (paramsArray.length() > 1) {
                    try {
                        org.json.JSONObject typedData = new org.json.JSONObject(paramsArray.getString(1));
                        String domain = typedData.optJSONObject("domain") != null ? 
                            typedData.getJSONObject("domain").optString("name", "DApp") : "DApp";
                        String primaryType = typedData.optString("primaryType", "");
                        dataHex = "Domain: " + domain + "\nType: " + primaryType;
                    } catch (Exception e) {
                        dataHex = "Typed data request";
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing params", e);
        }
        
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView);
        
        TextView typeView = new TextView(this);
        typeView.setText(actionType);
        typeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        typeView.setTextColor(Color.parseColor("#4A90D9"));
        typeView.setGravity(Gravity.CENTER);
        typeView.setPadding(0, dp(4), 0, dp(16));
        layout.addView(typeView);
        
        LinearLayout networkCard = createInfoCard("Network", getChainName(), getChainColor());
        layout.addView(networkCard);
        
        if (!method.contains("sign") || method.contains("Transaction")) {
            LinearLayout walletCard = createInfoCard("From", shortenAddress(currentAddress), "#FFFFFF");
            layout.addView(walletCard);
            
            if (!toAddress.isEmpty()) {
                LinearLayout toCard = createInfoCard("To", shortenAddress(toAddress), "#FFFFFF");
                layout.addView(toCard);
            }
            
            if (!valueAmount.equals("0") && !valueAmount.equals("0.000000")) {
                LinearLayout valueCard = createInfoCard("Amount", valueAmount + " " + getChainSymbol(), "#FFD700");
                layout.addView(valueCard);
            }
            
            if (!gasEstimate.isEmpty() && !gasEstimate.equals("0")) {
                LinearLayout gasCard = createInfoCard("Est. Gas", gasEstimate, "#888888");
                layout.addView(gasCard);
            }
            
            if (hasData && !functionName.isEmpty()) {
                LinearLayout funcCard = createInfoCard("Function", functionName, "#AAAAAA");
                layout.addView(funcCard);
            }
        } else {
            if (dataHex.length() > 0) {
                LinearLayout msgCard = new LinearLayout(this);
                msgCard.setOrientation(LinearLayout.VERTICAL);
                msgCard.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                msgParams.setMargins(0, dp(8), 0, dp(8));
                msgCard.setLayoutParams(msgParams);
                
                GradientDrawable msgBg = new GradientDrawable();
                msgBg.setColor(Color.parseColor("#2A2A3E"));
                msgBg.setCornerRadius(dp(10));
                msgCard.setBackground(msgBg);
                
                TextView msgLabel = new TextView(this);
                msgLabel.setText("Message");
                msgLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                msgLabel.setTextColor(Color.parseColor("#888888"));
                msgCard.addView(msgLabel);
                
                TextView msgValue = new TextView(this);
                msgValue.setText(dataHex.length() > 300 ? dataHex.substring(0, 300) + "..." : dataHex);
                msgValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                msgValue.setTextColor(Color.parseColor("#CCCCCC"));
                msgValue.setPadding(0, dp(4), 0, 0);
                msgCard.addView(msgValue);
                
                layout.addView(msgCard);
            }
        }
        
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
        layout.addView(spacer);
        
        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(0, dp(8), 0, 0);
        
        Button rejectBtn = new Button(this);
        rejectBtn.setText("REJECT");
        rejectBtn.setTextColor(Color.WHITE);
        rejectBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rejectBtn.setTypeface(null, Typeface.BOLD);
        rejectBtn.setAllCaps(false);
        GradientDrawable rejectBg = new GradientDrawable();
        rejectBg.setColor(Color.parseColor("#3A3A4E"));
        rejectBg.setCornerRadius(dp(25));
        rejectBtn.setBackground(rejectBg);
        rejectBtn.setPadding(dp(32), dp(14), dp(32), dp(14));
        LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rejectParams.setMargins(0, 0, dp(8), 0);
        rejectBtn.setLayoutParams(rejectParams);
        rejectBtn.setOnClickListener(v -> handleDialogReject(id));
        buttonContainer.addView(rejectBtn);
        
        Button confirmBtn = new Button(this);
        confirmBtn.setText("CONFIRM");
        confirmBtn.setTextColor(Color.WHITE);
        confirmBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        confirmBtn.setTypeface(null, Typeface.BOLD);
        confirmBtn.setAllCaps(false);
        GradientDrawable confirmBg = new GradientDrawable();
        confirmBg.setColor(Color.parseColor("#4CAF50"));
        confirmBg.setCornerRadius(dp(25));
        confirmBtn.setBackground(confirmBg);
        confirmBtn.setPadding(dp(32), dp(14), dp(32), dp(14));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        confirmParams.setMargins(dp(8), 0, 0, 0);
        confirmBtn.setLayoutParams(confirmParams);
        confirmBtn.setOnClickListener(v -> handleDialogConfirm(id, method, params));
        buttonContainer.addView(confirmBtn);
        
        layout.addView(buttonContainer);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
        builder.setView(scrollView);
        builder.setCancelable(false);
        
        currentConfirmDialog = builder.create();
        if (currentConfirmDialog.getWindow() != null) {
            currentConfirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(Color.parseColor("#1A1A2E"));
            dialogBg.setCornerRadius(dp(20));
            currentConfirmDialog.getWindow().setBackgroundDrawable(dialogBg);
        }
        currentConfirmDialog.show();
    }
    
    private LinearLayout createInfoCard(String label, String value, String valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(cardParams);
        
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#2A2A3E"));
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);
        
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setTextColor(Color.parseColor("#888888"));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(labelView);
        
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueView.setTextColor(Color.parseColor(valueColor));
        valueView.setTypeface(null, Typeface.BOLD);
        card.addView(valueView);
        
        return card;
    }
    
    private String formatWeiToEth(String hexValue) {
        try {
            if (hexValue == null || hexValue.isEmpty() || hexValue.equals("0x") || hexValue.equals("0x0")) {
                return "0";
            }
            String hex = hexValue.startsWith("0x") ? hexValue.substring(2) : hexValue;
            if (hex.isEmpty()) return "0";
            BigInteger wei = new BigInteger(hex, 16);
            BigDecimal eth = new BigDecimal(wei).divide(new BigDecimal("1000000000000000000"));
            if (eth.compareTo(BigDecimal.ZERO) == 0) return "0";
            return eth.stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return "0";
        }
    }
    
    private String formatGas(String hexValue) {
        try {
            if (hexValue == null || hexValue.isEmpty() || hexValue.equals("0x") || hexValue.equals("0x0")) {
                return "";
            }
            String hex = hexValue.startsWith("0x") ? hexValue.substring(2) : hexValue;
            if (hex.isEmpty()) return "";
            long gas = Long.parseLong(hex, 16);
            return String.format("%,d", gas);
        } catch (Exception e) {
            return "";
        }
    }
    
    private String decodeFunction(String data) {
        if (data == null || data.length() < 10) return "";
        String selector = data.substring(0, 10).toLowerCase();
        
        switch (selector) {
            case "0xa9059cbb": return "transfer(address,uint256)";
            case "0x23b872dd": return "transferFrom(address,address,uint256)";
            case "0x095ea7b3": return "approve(address,uint256)";
            case "0x38ed1739": return "swapExactTokensForTokens";
            case "0x7ff36ab5": return "swapExactETHForTokens";
            case "0x18cbafe5": return "swapExactTokensForETH";
            case "0x5c11d795": return "swapExactTokensForTokensSupportingFeeOnTransferTokens";
            case "0xfb3bdb41": return "swapETHForExactTokens";
            case "0x4a25d94a": return "swapTokensForExactETH";
            case "0x2e1a7d4d": return "withdraw(uint256)";
            case "0xd0e30db0": return "deposit()";
            case "0xa0712d68": return "mint(uint256)";
            case "0x40c10f19": return "mint(address,uint256)";
            case "0x42842e0e": return "safeTransferFrom(address,address,uint256)";
            case "0xb88d4fde": return "safeTransferFrom(address,address,uint256,bytes)";
            case "0xa22cb465": return "setApprovalForAll(address,bool)";
            case "0xc04b8d59": return "exactInput(tuple)";
            case "0x414bf389": return "exactInputSingle(tuple)";
            case "0xf28c0498": return "exactOutput(tuple)";
            case "0xdb3e2198": return "exactOutputSingle(tuple)";
            default: return "Contract Call";
        }
    }
    
    private void handleDialogReject(int id) {
        if (currentConfirmDialog != null) {
            currentConfirmDialog.dismiss();
            currentConfirmDialog = null;
        }
        
        handleWeb3Response(id, null, "User rejected the request");
        
        pendingRequestId = -1;
        pendingMethod = "";
        pendingParams = "";
    }
    
    private void handleDialogConfirm(int id, String method, String params) {
        Log.d(TAG, "handleDialogConfirm called for method: " + method);
        
        if (currentConfirmDialog != null) {
            currentConfirmDialog.dismiss();
            currentConfirmDialog = null;
        }
        
        Intent intent = new Intent(ACTION_WEB3_REQUEST);
        intent.putExtra("id", id);
        intent.putExtra("method", method);
        intent.putExtra("params", params);
        intent.putExtra("confirmed", true);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Log.d(TAG, "Broadcast sent for confirmed request");
        
        pendingRequestId = -1;
        pendingMethod = "";
        pendingParams = "";
    }
    
    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
    
    private String shortenAddress(String address) {
        if (address == null || address.length() < 10) return address != null ? address : "";
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
    
    private String getChainSymbol() {
        switch (currentChainId) {
            case 1: return "ETH";
            case 56: return "BNB";
            case 137: return "MATIC";
            case 43114: return "AVAX";
            case 42161: return "ETH";
            case 10: return "ETH";
            case 250: return "FTM";
            case 25: return "CRO";
            case 8453: return "ETH";
            default: return "ETH";
        }
    }
    
    private String getChainName() {
        switch (currentChainId) {
            case 1: return "Ethereum Mainnet";
            case 56: return "BNB Smart Chain";
            case 137: return "Polygon";
            case 43114: return "Avalanche C-Chain";
            case 42161: return "Arbitrum One";
            case 10: return "Optimism";
            case 250: return "Fantom Opera";
            case 25: return "Cronos";
            case 8453: return "Base";
            default: return "Chain " + currentChainId;
        }
    }
    
    private String getChainColor() {
        switch (currentChainId) {
            case 1: return "#627EEA";
            case 56: return "#F3BA2F";
            case 137: return "#8247E5";
            case 43114: return "#E84142";
            case 42161: return "#28A0F0";
            case 10: return "#FF0420";
            case 250: return "#1969FF";
            case 25: return "#002D74";
            case 8453: return "#0052FF";
            default: return "#888888";
        }
    }
    
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    private String getRpcUrl(int chainId) {
        switch (chainId) {
            case 1: return "https://eth.llamarpc.com";
            case 56: return "https://bsc-dataseed1.binance.org";
            case 137: return "https://polygon-rpc.com";
            case 43114: return "https://api.avax.network/ext/bc/C/rpc";
            case 42161: return "https://arb1.arbitrum.io/rpc";
            case 10: return "https://mainnet.optimism.io";
            case 8453: return "https://mainnet.base.org";
            default: return "https://eth.llamarpc.com";
        }
    }
    
    private String buildInjectionScript() {
        String hexChainId = "0x" + Integer.toHexString(currentChainId);
        
        return "(function(){" +
            "'use strict';" +
            "if(window._vkInjected)return;" +
            "window._vkInjected=true;" +
            
            "var _id=1;" +
            "var _addr='" + currentAddress + "';" +
            "var _chainId='" + hexChainId + "';" +
            "var _netVersion='" + currentChainId + "';" +
            "var _callbacks={};" +
            "var _listeners={};" +
            
            "var _rpcs={1:'https://eth.llamarpc.com',56:'https://bsc-dataseed1.binance.org',137:'https://polygon-rpc.com',43114:'https://api.avax.network/ext/bc/C/rpc',42161:'https://arb1.arbitrum.io/rpc',10:'https://mainnet.optimism.io',8453:'https://mainnet.base.org'};" +
            "var _rpcUrl=_rpcs[" + currentChainId + "]||'https://eth.llamarpc.com';" +
            
            "window._vkCallbacks=_callbacks;" +
            
            "window.__vkUpdate=function(addr,chain,rpc){" +
            "_addr=addr;_chainId=chain;_rpcUrl=rpc||_rpcUrl;" +
            "_netVersion=String(parseInt(chain,16));" +
            "provider.selectedAddress=_addr;" +
            "provider.chainId=_chainId;" +
            "provider.networkVersion=_netVersion;" +
            "emit('accountsChanged',[_addr]);" +
            "emit('chainChanged',_chainId);" +
            "};" +
            
            "function rpc(method,params){" +
            "return fetch(_rpcUrl,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:Date.now(),method:method,params:params||[]})}).then(function(r){return r.json();}).then(function(d){if(d.error)throw new Error(d.error.message);return d.result;});" +
            "}" +
            
            "function on(e,fn){if(!_listeners[e])_listeners[e]=[];_listeners[e].push(fn);return provider;}" +
            "function off(e,fn){if(_listeners[e])_listeners[e]=_listeners[e].filter(function(f){return f!==fn;});return provider;}" +
            "function emit(e){var args=[].slice.call(arguments,1);if(_listeners[e])_listeners[e].slice().forEach(function(fn){try{fn.apply(null,args);}catch(x){}});return true;}" +
            
            "function bridge(method,params){" +
            "return new Promise(function(resolve,reject){" +
            "var id=_id++;_callbacks[id]={resolve:resolve,reject:reject};" +
            "try{VaultKeyNative.postMessage(JSON.stringify({id:id,method:method,params:params}));}catch(e){delete _callbacks[id];reject(e);}" +
            "setTimeout(function(){if(_callbacks[id]){delete _callbacks[id];reject(new Error('Timeout'));}},120000);" +
            "});" +
            "}" +
            
            "var directRpc=['eth_blockNumber','eth_getBlockByNumber','eth_getBlockByHash','eth_call','eth_getBalance','eth_getCode','eth_getStorageAt','eth_getTransactionCount','eth_getTransactionByHash','eth_getTransactionReceipt','eth_getLogs','eth_estimateGas','eth_gasPrice','eth_feeHistory','eth_maxPriorityFeePerGas','net_listening','web3_clientVersion'];" +
            
            "function request(args){" +
            "var method=args.method;var params=args.params||[];" +
            "if(method==='eth_accounts')return Promise.resolve(_addr?[_addr]:[]);" +
            "if(method==='eth_requestAccounts'){emit('connect',{chainId:_chainId});return Promise.resolve([_addr]);}" +
            "if(method==='eth_chainId')return Promise.resolve(_chainId);" +
            "if(method==='net_version')return Promise.resolve(_netVersion);" +
            "if(method==='eth_coinbase')return Promise.resolve(_addr);" +
            "if(method==='wallet_requestPermissions')return Promise.resolve([{parentCapability:'eth_accounts'}]);" +
            "if(method==='wallet_getPermissions')return Promise.resolve([{parentCapability:'eth_accounts'}]);" +
            "if(method==='wallet_switchEthereumChain'){var c=params[0]&&params[0].chainId;if(c){var n=parseInt(c,16);if(_rpcs[n]){_chainId=c;_netVersion=String(n);_rpcUrl=_rpcs[n];provider.chainId=_chainId;emit('chainChanged',_chainId);return Promise.resolve(null);}return Promise.reject({code:4902,message:'Chain not supported'});}return Promise.resolve(null);}" +
            "if(method==='wallet_addEthereumChain')return Promise.resolve(null);" +
            "if(method==='wallet_watchAsset')return Promise.resolve(true);" +
            "if(directRpc.indexOf(method)!==-1)return rpc(method,params);" +
            "if(method==='eth_sendTransaction'||method==='eth_signTransaction'||method==='personal_sign'||method==='eth_sign'||method==='eth_signTypedData'||method==='eth_signTypedData_v3'||method==='eth_signTypedData_v4')return bridge(method,params);" +
            "return rpc(method,params);" +
            "}" +
            
            "var provider={" +
            "isMetaMask:true,isTrust:true,isVaultKey:true," +
            "selectedAddress:_addr,chainId:_chainId,networkVersion:_netVersion," +
            "isConnected:function(){return true;}," +
            "request:request," +
            "send:function(m,p){if(typeof m==='string')return request({method:m,params:p});return request(m);}," +
            "sendAsync:function(req,cb){request({method:req.method,params:req.params}).then(function(r){cb(null,{id:req.id,jsonrpc:'2.0',result:r});}).catch(function(e){cb(e);});}," +
            "on:on,off:off,removeListener:off,emit:emit," +
            "enable:function(){return request({method:'eth_requestAccounts'});}" +
            "};" +
            
            "window.ethereum=provider;" +
            "window.web3={currentProvider:provider};" +
            
            "var info={uuid:'vaultkey-1',name:'VaultKey',icon:'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiMxYTFhMmUiLz48dGV4dCB4PSIxNiIgeT0iMjEiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiNmZmYiIHRleHQtYW5jaG9yPSJtaWRkbGUiPuKXiDwvdGV4dD48L3N2Zz4=',rdns:'app.vaultkey.wallet'};" +
            "var announceProvider=function(){try{window.dispatchEvent(new CustomEvent('eip6963:announceProvider',{detail:Object.freeze({info:info,provider:provider})}));}catch(e){}};" +
            "window.addEventListener('eip6963:requestProvider',announceProvider);" +
            "announceProvider();" +
            
            "provider.emit('connect',{chainId:_chainId});" +
            "console.log('VaultKey wallet injected');" +
            "})();";
    }
    
    private class WalletBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(message);
                int id = json.getInt("id");
                String method = json.getString("method");
                String params = json.optString("params", "[]");
                
                if (json.has("params")) {
                    Object paramsObj = json.get("params");
                    if (paramsObj instanceof org.json.JSONArray) {
                        params = paramsObj.toString();
                    }
                }
                
                Log.d(TAG, "WalletBridge received: " + method);
                sendWeb3Request(id, method, params);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing message", e);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        if (responseReceiver != null) lbm.unregisterReceiver(responseReceiver);
        if (closeReceiver != null) lbm.unregisterReceiver(closeReceiver);
        if (updateReceiver != null) lbm.unregisterReceiver(updateReceiver);
        if (resumeReceiver != null) lbm.unregisterReceiver(resumeReceiver);
        if (pinRequestReceiver != null) lbm.unregisterReceiver(pinRequestReceiver);
        
        if (currentConfirmDialog != null && currentConfirmDialog.isShowing()) {
            currentConfirmDialog.dismiss();
        }
        if (currentPinDialog != null && currentPinDialog.isShowing()) {
            currentPinDialog.dismiss();
        }
        
        sendBrowserEvent("", false);
    }
    
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
