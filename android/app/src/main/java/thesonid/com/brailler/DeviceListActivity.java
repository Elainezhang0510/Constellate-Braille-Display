package thesonid.com.brailler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "DeviceListActivity";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter btAdapter;
    private ArrayAdapter<String> newDevicesArrayAdapter;
    private Button scanButton;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean value : result.values()) {
                    if (!Boolean.TRUE.equals(value)) {
                        granted = false;
                        break;
                    }
                }
                if (granted) {
                    doDiscovery();
                } else {
                    Toast.makeText(this, R.string.permission_declined, Toast.LENGTH_SHORT).show();
                    scanButton.setVisibility(View.VISIBLE);
                }
            });

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }
                try {
                if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String name = device.getName();
                    if (name == null) {
                        name = "Unknown device";
                    }
                    newDevicesArrayAdapter.add(name + "\n" + device.getAddress());
                }
                } catch (SecurityException denied) { Toast.makeText(context, R.string.permission_declined, Toast.LENGTH_SHORT).show(); }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setTitle(R.string.select_device);
                if (newDevicesArrayAdapter.getCount() == 0) {
                    newDevicesArrayAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };

    private final AdapterView.OnItemClickListener deviceClickListener = (av, v, arg2, arg3) -> {
        if (btAdapter != null) {
            try { btAdapter.cancelDiscovery(); } catch (SecurityException denied) { /* Permission was revoked. */ }
        }
        String info = ((TextView) v).getText().toString();
        if (info.length() < 17 || !BluetoothAdapter.checkBluetoothAddress(info.substring(info.length() - 17))) {
            return;
        }
        String address = info.substring(info.length() - 17);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
        setResult(Activity.RESULT_OK, intent);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        setResult(Activity.RESULT_CANCELED);

        scanButton = findViewById(R.id.button_scan);
        scanButton.setOnClickListener(v -> requestScanPermissions());

        ArrayAdapter<String> pairedDevicesArrayAdapter =
                new ArrayAdapter<>(this, R.layout.device_name);
        newDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        ListView pairedListView = findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(deviceClickListener);

        ListView newDevicesListView = findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(newDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(deviceClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            finish();
            return;
        }

        try {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                if (name == null) {
                    name = "Unknown device";
                }
                pairedDevicesArrayAdapter.add(name + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesArrayAdapter.add(getString(R.string.none_paired));
        }
        } catch (SecurityException denied) { requestScanPermissions(); }
    }

    private void requestScanPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (permissions.isEmpty()) {
            doDiscovery();
        } else {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btAdapter != null) {
            try { btAdapter.cancelDiscovery(); } catch (SecurityException denied) { /* Permission was revoked. */ }
        }
        unregisterReceiver(receiver);
    }

    private void doDiscovery() {
        Log.d(TAG, "doDiscovery()");
        scanButton.setVisibility(View.GONE);
        setTitle(R.string.scanning);
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        try {
        if (btAdapter.isDiscovering()) {
            try { btAdapter.cancelDiscovery(); } catch (SecurityException denied) { /* Permission was revoked. */ }
        }
        btAdapter.startDiscovery();
        } catch (SecurityException denied) { scanButton.setVisibility(View.VISIBLE); Toast.makeText(this, R.string.permission_declined, Toast.LENGTH_SHORT).show(); }
    }
}
