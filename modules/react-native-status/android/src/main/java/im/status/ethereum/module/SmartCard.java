package im.status.ethereum.module;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.support.annotation.Nullable;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import im.status.hardwallet_lite_android.io.APDUException;
import im.status.hardwallet_lite_android.io.CardChannel;
import im.status.hardwallet_lite_android.io.CardListener;
import im.status.hardwallet_lite_android.io.CardManager;
import im.status.hardwallet_lite_android.wallet.WalletAppletCommandSet;

public class SmartCard extends BroadcastReceiver implements CardListener {
    private CardManager cardManager;
    private Activity activity;
    private ReactContext reactContext;
    private NfcAdapter nfcAdapter;
    private CardChannel cardChannel;

    public SmartCard(Activity activity, ReactContext reactContext) {
        this.cardManager = new CardManager();
        this.cardManager.setCardListener(this);
        this.activity = activity;
        this.reactContext = reactContext;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity.getBaseContext());
    }

    public String getName() {
        return "SmartCard";
    }

    public void log(String s) {
        Log.d("installer-debug", s);
    }

    public void start() {
        this.cardManager.start();
        if (this.nfcAdapter != null) {
            IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            activity.registerReceiver(this, filter);
            nfcAdapter.enableReaderMode(activity, this.cardManager, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        } else {
            log("not support in this device");
        }
    }

    @Override
    public void onConnected(final CardChannel channel) {
        this.cardChannel = channel;
        sendEvent(reactContext, "scOnConnected", null);
    }

    @Override
    public void onDisconnected() {
        sendEvent(reactContext, "scOnDisconnected", null);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
        boolean on = false;
        switch (state) {
            case NfcAdapter.STATE_ON:
                log("NFC ON");
            case NfcAdapter.STATE_OFF:
                log("NFC OFF");
            default:
                log("other");
        }
    }

    public boolean isNfcSupported() {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    public boolean isNfcEnabled() {
        return nfcAdapter.isEnabled();
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    public SmartCardSecrets init() throws IOException, APDUException, NoSuchAlgorithmException, InvalidKeySpecException {
        WalletAppletCommandSet cmdSet = new WalletAppletCommandSet(this.cardChannel);
        cmdSet.select().checkOK();

        SmartCardSecrets s = SmartCardSecrets.generate();
        cmdSet.init(s.getPin(), s.getPuk(), s.getPairingPassword()).checkOK();

        return s;
    }
}
