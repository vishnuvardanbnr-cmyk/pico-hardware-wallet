package app.vaultkey.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DAppBrowser")
public class DAppBrowserPlugin extends Plugin {
    private static final String TAG = "DAppBrowserPlugin";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver browserEventReceiver;
    private BroadcastReceiver web3RequestReceiver;
    private BroadcastReceiver pinResponseReceiver;
    private boolean isBrowserOpen = false;

    @Override
    public void load() {
        super.load();
        setupBroadcastReceivers();
    }

    private void setupBroadcastReceivers() {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getContext());
        
        browserEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String url = intent.getStringExtra("url");
                boolean loading = intent.getBooleanExtra("loading", false);
                
                if (url == null || url.isEmpty()) {
                    isBrowserOpen = false;
                } else {
                    isBrowserOpen = true;
                }
                
                JSObject event = new JSObject();
                event.put("url", url != null ? url : "");
                event.put("loading", loading);
                notifyListeners("browserEvent", event);
            }
        };
        
        web3RequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int id = intent.getIntExtra("id", 0);
                String method = intent.getStringExtra("method");
                String params = intent.getStringExtra("params");
                boolean confirmed = intent.getBooleanExtra("confirmed", false);
                
                JSObject event = new JSObject();
                event.put("id", id);
                event.put("method", method != null ? method : "");
                event.put("params", params != null ? params : "[]");
                event.put("confirmed", confirmed);
                notifyListeners("web3Request", event);
            }
        };
        
        pinResponseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String pin = intent.getStringExtra("pin");
                String walletGroupId = intent.getStringExtra("walletGroupId");
                boolean cancelled = intent.getBooleanExtra("cancelled", false);
                
                JSObject event = new JSObject();
                event.put("pin", pin != null ? pin : "");
                event.put("walletGroupId", walletGroupId != null ? walletGroupId : "");
                event.put("cancelled", cancelled);
                notifyListeners("pinResponse", event);
            }
        };
        
        lbm.registerReceiver(browserEventReceiver, new IntentFilter(DAppBrowserActivity.ACTION_BROWSER_EVENT));
        lbm.registerReceiver(web3RequestReceiver, new IntentFilter(DAppBrowserActivity.ACTION_WEB3_REQUEST));
        lbm.registerReceiver(pinResponseReceiver, new IntentFilter(DAppBrowserActivity.ACTION_PIN_RESPONSE));
    }

    @PluginMethod
    public void open(PluginCall call) {
        String url = call.getString("url", "");
        String address = call.getString("address", "");
        int chainId = call.getInt("chainId", 1);
        
        if (url.isEmpty()) {
            call.reject("URL is required");
            return;
        }

        Log.d(TAG, "Opening DApp browser: " + url);
        
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(getContext(), DAppBrowserActivity.class);
                intent.putExtra(DAppBrowserActivity.EXTRA_URL, url);
                intent.putExtra(DAppBrowserActivity.EXTRA_ADDRESS, address);
                intent.putExtra(DAppBrowserActivity.EXTRA_CHAIN_ID, chainId);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                
                isBrowserOpen = true;
                
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                Log.e(TAG, "Error opening browser", e);
                call.reject("Failed to open browser: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void close(PluginCall call) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(DAppBrowserActivity.ACTION_CLOSE_BROWSER);
                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
                isBrowserOpen = false;
            } catch (Exception e) {
                Log.e(TAG, "Error closing browser", e);
            }
            
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void updateAccount(PluginCall call) {
        String address = call.getString("address", "");
        int chainId = call.getInt("chainId", 1);
        
        Intent intent = new Intent(DAppBrowserActivity.ACTION_UPDATE_ACCOUNT);
        intent.putExtra("address", address);
        intent.putExtra("chainId", chainId);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void sendResponse(PluginCall call) {
        int id = call.getInt("id", 0);
        String result = call.getString("result", "null");
        String error = call.getString("error", "");
        
        Intent intent = new Intent(DAppBrowserActivity.ACTION_WEB3_RESPONSE);
        intent.putExtra("id", id);
        intent.putExtra("result", result);
        intent.putExtra("error", error);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        
        // Automatically bring browser back to foreground after sending response
        Intent resumeIntent = new Intent(DAppBrowserActivity.ACTION_RESUME_BROWSER);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(resumeIntent);
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }
    
    @PluginMethod
    public void resumeBrowser(PluginCall call) {
        Intent intent = new Intent(DAppBrowserActivity.ACTION_RESUME_BROWSER);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }
    
    @PluginMethod
    public void requestPin(PluginCall call) {
        String walletGroupId = call.getString("walletGroupId", "");
        
        Intent intent = new Intent(DAppBrowserActivity.ACTION_REQUEST_PIN);
        intent.putExtra("walletGroupId", walletGroupId);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getContext());
        if (browserEventReceiver != null) {
            lbm.unregisterReceiver(browserEventReceiver);
        }
        if (web3RequestReceiver != null) {
            lbm.unregisterReceiver(web3RequestReceiver);
        }
        if (pinResponseReceiver != null) {
            lbm.unregisterReceiver(pinResponseReceiver);
        }
    }
}
