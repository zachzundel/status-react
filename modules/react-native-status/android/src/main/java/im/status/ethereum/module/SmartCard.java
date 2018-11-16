package im.status.ethereum.module;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.util.Log;
import com.facebook.react.bridge.*;

import im.status.hardwallet_lite_android.io.CardChannel;
import im.status.hardwallet_lite_android.io.CardListener;
import im.status.hardwallet_lite_android.io.CardManager;


public class SmartCard extends BroadcastReceiver implements CardListener {
    private CardManager cardManager;
    private Callback onCardConnected;
    private Activity activity;
    private NfcAdapter nfcAdapter;

    private Callback onCardConnectedCallback;
    private Callback onCardDisconnectedCallback;

    public SmartCard(Activity activity) {
        this.cardManager = new CardManager();
        this.cardManager.setCardListener(this);
        this.activity = activity;
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

    public void setOnCardConnectedHandler(Callback callback) {
        this.onCardConnectedCallback = callback;
    }

    public void setOnCardDisconnectedCallback(Callback callback) {
        this.onCardDisconnectedCallback = callback;
    }

    @Override
    public void onConnected(final CardChannel channel) {
        if (this.onCardConnectedCallback != null) {
            this.onCardConnectedCallback.invoke();
        }
    }

    @Override
    public void onDisconnected() {
        if (this.onCardDisconnectedCallback != null) {
            this.onCardDisconnectedCallback.invoke();
        }
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
}
