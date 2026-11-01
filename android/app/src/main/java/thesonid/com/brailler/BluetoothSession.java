package thesonid.com.brailler;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/** Process-owned connection: navigation only attaches/detaches the current screen. */
final class BluetoothSession {
    private static BluetoothSession instance;
    private static final java.util.Set<Runnable> observers =
            new java.util.concurrent.CopyOnWriteArraySet<>();
    private final BluetoothChatService service;
    private Handler screen;
    private String deviceName;

    private BluetoothSession(Context context) {
        Handler relay = new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message message) {
                if (message.what == Constants.MESSAGE_DEVICE_NAME) {
                    deviceName = message.getData().getString(Constants.DEVICE_NAME);
                }
                if (screen != null) screen.sendMessage(Message.obtain(message));
                if (message.what == Constants.MESSAGE_STATE_CHANGE
                        || message.what == Constants.MESSAGE_DEVICE_NAME) {
                    for (Runnable observer : observers) observer.run();
                }
            }
        };
        service = new BluetoothChatService(context.getApplicationContext(), relay);
    }

    static synchronized BluetoothSession get(Context context) {
        if (instance == null) instance = new BluetoothSession(context);
        return instance;
    }

    static void observe(Runnable observer) { observers.add(observer); }
    static void unobserve(Runnable observer) { observers.remove(observer); }

    static synchronized int state() {
        return instance == null ? BluetoothChatService.STATE_NONE : instance.service.getState();
    }

    static synchronized String connectedName() {
        return instance == null || instance.deviceName == null ? "Braille board" : instance.deviceName;
    }

    BluetoothChatService attach(Handler handler) {
        screen = handler;
        Message device = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle data = new Bundle();
        data.putString(Constants.DEVICE_NAME, deviceName);
        device.setData(data);
        handler.sendMessage(device);
        handler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, service.getState(), -1).sendToTarget();
        return service;
    }

    void detach(Handler handler) {
        if (screen == handler) screen = null;
        handler.removeCallbacksAndMessages(null);
    }

    static synchronized void disconnect() {
        if (instance != null) {
            instance.service.stop();
            instance.deviceName = null;
        }
    }

    /** Reuses the live board session; disconnected callers open the existing connection activity. */
    static synchronized boolean sendWordIfConnected(String word) {
        if (!ConstellateRules.validWord(word) || instance == null
                || instance.service.getState() != BluetoothChatService.STATE_CONNECTED) return false;
        try {
            org.json.JSONObject command = new org.json.JSONObject();
            command.put("cmd", "load");
            command.put("text", ConstellateRules.word(word));
            instance.service.write((command.toString() + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (org.json.JSONException | SecurityException error) {
            return false;
        }
    }
}
