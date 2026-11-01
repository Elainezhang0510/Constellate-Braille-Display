package thesonid.com.brailler;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class BluetoothChatFragment extends AppCompatDialogFragment {

    private static final String TAG = "BluetoothChatFragment";
    private static final String WIFI_RESULT_KEY = WifiDialogFragment.WIFI_RESULT_KEY;
    private static final String ARG_WORD = "word";

    public static BluetoothChatFragment forWord(String word) {
        BluetoothChatFragment fragment = new BluetoothChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WORD, word == null ? "" : word);
        fragment.setArguments(args);
        return fragment;
    }

    static String currentStatus = "not connected";

    private ListView conversationView;
    private EditText outEditText;
    private Button sendButton;
    private AlertDialog progressDialog;

    private String connectedDeviceName;
    private ArrayAdapter<String> conversationArrayAdapter;
    private StringBuilder outStringBuffer;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothChatService chatService;
    private BluetoothSession session;
    private TextView brailleResult;
    private final StringBuilder replyBuffer = new StringBuilder();

    private boolean isRead = false;
    private boolean isCountdown = false;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    setupChat();
                } else {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> connectDeviceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    connectDevice(result.getData());
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
                activity.finish();
            }
        }

        getParentFragmentManager().setFragmentResultListener(
                WIFI_RESULT_KEY, this, (requestKey, result) -> {
                    if (chatService == null
                            || chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
                        Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    showProgressDialog();
                    isCountdown = true;
                    handler.postDelayed(watchDogTimeOut, 30000);

                    String ssid = result.getString("SSID", "");
                    String pwd = result.getString("PWD", "");
                    sendWifiCredentials(ssid, pwd);
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (bluetoothAdapter == null) {
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            if (session == null) session = BluetoothSession.get(requireContext());
            chatService = session.attach(handler);
        }
    }

    @Override
    public void onStop() {
        if (session != null) session.detach(handler);
        isCountdown = false;
        replyBuffer.setLength(0);
        hideProgressDialog();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (session != null) session.detach(handler);
        handler.removeCallbacksAndMessages(null);
        hideProgressDialog();
        progressDialog = null;
        conversationView = null;
        outEditText = null;
        sendButton = null;
        brailleResult = null;
        conversationArrayAdapter = null;
        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
        Button connectBtn = view.findViewById(R.id.bluetooth_connect_button);
        Button wifiBtn = view.findViewById(R.id.wifi_connect_button);
        String word = getArguments() == null ? "" : getArguments().getString(ARG_WORD, "");
        View controls = view.findViewById(R.id.braille_controls);
        if (word.matches("[a-zA-Z]{1,128}")) {
            controls.setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.braille_word)).setText("Selected word: " + word);
            brailleResult = view.findViewById(R.id.braille_result);
            view.findViewById(R.id.braille_load).setOnClickListener(v -> sendBoard("load", word));
        }

        connectBtn.setOnClickListener(v -> {
            Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
            connectDeviceLauncher.launch(serverIntent);
        });

        wifiBtn.setOnClickListener(v -> showWifiDialog());
        view.findViewById(R.id.bluetooth_disconnect_button).setOnClickListener(v ->
                BluetoothSession.disconnect());
        ConstellateUi ui = ConstellateUi.forAccount(requireContext());
        view.setBackgroundColor(ui.bg);ui.styleTextTree(view);
        for (Button button : new Button[]{connectBtn, wifiBtn,
                view.findViewById(R.id.bluetooth_disconnect_button),
                view.findViewById(R.id.braille_load)}) {
            button.setAllCaps(false);button.setTextColor(ui.bg);button.setTextSize(17);
            button.setMinHeight(ui.dp(60));button.setBackgroundTintList(null);
            button.setBackground(ui.shape(ui.ink,ui.line,12,2));
        }
        styleDisconnect(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        conversationView = view.findViewById(R.id.in);
        outEditText = view.findViewById(R.id.edit_text_out);
        sendButton = view.findViewById(R.id.button_send);
        setupChatViews();
    }

    private void setupChatViews() {
        conversationArrayAdapter = new ArrayAdapter<String>(
                requireActivity(), R.layout.message) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View itemView = super.getView(position, convertView, parent);
                TextView textView = itemView.findViewById(R.id.listItem);
                textView.setTextColor(isRead ? Color.BLUE : Color.RED);
                return itemView;
            }
        };
        conversationView.setAdapter(conversationArrayAdapter);

        outEditText.setOnEditorActionListener(mWriteListener);
        sendButton.setOnClickListener(v -> {
            String message = outEditText.getText().toString();
            sendMessage(message);
        });

        outStringBuffer = new StringBuilder();
    }

    private void setupChat() {
        session = BluetoothSession.get(requireContext());
        chatService = session.attach(handler);
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.progress_dialog_title)
                    .setMessage(getString(R.string.progress_dialog_message))
                    .setCancelable(false)
                    .create();
        }
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void sendMessage(String message) {
        if (chatService == null || chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!message.isEmpty()) {
            chatService.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            outStringBuffer.setLength(0);
            outEditText.setText(outStringBuffer);
        }
    }

    private void sendBoard(String command, String word) {
        if (chatService == null || chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            brailleResult.setText("Board is not connected.");
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("cmd", command);
            if (word != null) json.put("text", word);
            chatService.write((json.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            brailleResult.setText("Waiting for board response…");
        } catch (JSONException e) {
            brailleResult.setText("Could not prepare the command.");
        }
    }

    private void sendWifiCredentials(String ssid, String pwd) {
        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ssid.isEmpty()) {
            JSONObject json = new JSONObject();
            try {
                json.put("SSID", ssid);
                json.put("PWD", pwd);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to build WiFi JSON", e);
                return;
            }
            chatService.write((json.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            outStringBuffer.setLength(0);
            outEditText.setText(outStringBuffer);
        }
    }

    private final TextView.OnEditorActionListener mWriteListener = (view, actionId, event) -> {
        if (actionId == EditorInfo.IME_NULL && event != null
                && event.getAction() == KeyEvent.ACTION_UP) {
            sendMessage(view.getText().toString());
        }
        return true;
    };

    private void setStatus(int resId) {
        setStatus(getString(resId));
    }

    private void updateConnectionLabel(CharSequence value) {
        View view=getView();
        if(view!=null) {
            ((TextView)view.findViewById(R.id.wifi_connection_status)).setText(value);
            styleDisconnect(view);
        }
    }

    private void styleDisconnect(View view) {
        ConstellateUi ui = ConstellateUi.forAccount(requireContext());
        boolean connected = chatService != null
                && chatService.getState() == BluetoothChatService.STATE_CONNECTED;
        Button button = view.findViewById(R.id.bluetooth_disconnect_button);
        button.setEnabled(connected);
        button.setAlpha(1f);
        button.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable background =
                ui.shape(connected ? Color.BLACK : Color.TRANSPARENT, ui.line, 12, 2);
        if (!connected) background.setStroke(ui.dp(1), ui.line, ui.dp(6), ui.dp(4));
        button.setBackground(background);
        button.setTextColor(connected ? Color.WHITE : ui.ink);
    }

    private void setStatus(CharSequence subTitle) {
        currentStatus=subTitle.toString();
        updateConnectionLabel(subTitle);
        FragmentActivity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            AppCompatActivity appCompatActivity = (AppCompatActivity) activity;
            if (appCompatActivity.getSupportActionBar() != null) {
                currentStatus = subTitle.toString();
                appCompatActivity.getSupportActionBar().setSubtitle(subTitle);
            }
        }
    }

    private final Runnable watchDogTimeOut = () -> {
        isCountdown = false;
        hideProgressDialog();
        Toast.makeText(getActivity(), "No response from RPi", Toast.LENGTH_LONG).show();
    };

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            if (activity == null || getView() == null || conversationArrayAdapter == null) return;
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            replyBuffer.setLength(0);
                            setStatus(getString(R.string.title_connected_to, connectedDeviceName));
                            conversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            replyBuffer.setLength(0);
                            setStatus(R.string.title_not_connected);
                            break;
                        default:
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    isRead = false;
                    byte[] writeBuf = (byte[]) msg.obj;
                    conversationArrayAdapter.add("Command:  " + new String(writeBuf));
                    break;
                case Constants.MESSAGE_READ:
                    isRead = true;
                    replyBuffer.append((String) msg.obj);
                    if (replyBuffer.length() > 4096) replyBuffer.setLength(0);
                    int end;
                    while ((end = replyBuffer.indexOf("\n")) >= 0) {
                        String readMessage = replyBuffer.substring(0, end).trim();
                        replyBuffer.delete(0, end + 1);
                        if (isJson(readMessage)) {
                            try {
                                JSONObject response = new JSONObject(readMessage);
                                if (response.has("ok")) handleBoardReply(response);
                                else if (response.has("result")) handleCallback(readMessage);
                            } catch (JSONException ignored) { }
                        } else if (!readMessage.isEmpty()) {
                            conversationArrayAdapter.add(readMessage);
                        }
                    }
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    connectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (connectedDeviceName != null) {
                        Toast.makeText(activity, "Connected to " + connectedDeviceName,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (activity != null) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void connectDevice(Intent data) {
        String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        if (address == null || bluetoothAdapter == null) {
            return;
        }
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        chatService.connect(device, true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.wifi_configuration) {
            showWifiDialog();
            return true;
        }
        if (itemId == R.id.insecure_connect_scan) {
            connectDeviceLauncher.launch(new Intent(getActivity(), DeviceListActivity.class));
            return true;
        }
        if (itemId == R.id.discoverable) {
            ensureDiscoverable();
            return true;
        }
        return false;
    }

    private void ensureDiscoverable() {
        try {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
        } catch (SecurityException e) {
            Toast.makeText(getContext(), R.string.permission_declined, Toast.LENGTH_LONG).show();
        }
    }

    public void showWifiDialog() {
        WifiDialogFragment dialog = WifiDialogFragment.newInstance();
        dialog.show(getParentFragmentManager(), "wifi_dialog");
    }

    private boolean isJson(String str) {
        try {
            new JSONObject(str);
            return true;
        } catch (JSONException ex) {
            return false;
        }
    }

    private void handleBoardReply(JSONObject response) {
        if (brailleResult == null) return;
        if (!response.optBoolean("ok")) {
            brailleResult.setText("Board rejected command: " + response.optString("error", "unknown error"));
        } else {
            String state = response.optString("state");
            brailleResult.setText(("queued".equals(state) ? "Queued " : "Requested ")
                    + response.optString("char") + " · letter "
                    + response.optInt("index") + " of " + response.optInt("total")
                    + ". Dots: " + response.optString("dots"));
        }
    }

    private void handleCallback(String str) {
        if (isCountdown) {
            handler.removeCallbacks(watchDogTimeOut);
            isCountdown = false;
        }
        hideProgressDialog();
        try {
            JSONObject json = new JSONObject(str);
            String result = json.optString("result", "");
            String ip = json.optString("IP", "");
            if (!"SUCCESS".equals(result)) {
                Toast.makeText(getActivity(), R.string.config_fail, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), getString(R.string.config_success) + ip,
                        Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(getActivity(), "SOMETHING WENT WRONG", Toast.LENGTH_LONG).show();
        }
    }
}
