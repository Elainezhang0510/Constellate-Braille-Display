package thesonid.com.brailler;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
  public static final String EXTRA_WORD = "thesonid.com.brailler.EXTRA_WORD";
  private LinearLayout permissionPanel;
  private final ActivityResultLauncher<String[]> permissions =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(),
          result -> showWizardIfAllowed());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.ConstellateTheme);
    super.onCreate(savedInstanceState);
    if (!FirebaseProvider.isConfigured(this)
        || FirebaseAuth.getInstance().getCurrentUser() == null) {
      startActivity(new Intent(this, MainActivityBraille.class));
      finish();
      return;
    }
    ConstellateUi ui = ConstellateUi.forAccount(this);
    LinearLayout root = ui.column();
    root.setBackgroundColor(ui.bg);
    ui.padding(root, 18, 16);
    ui.title(root, getIntent().hasExtra(EXTRA_WORD) ? "Send to Braille board" : "Set up Wi-Fi", 30);
    permissionPanel = ui.column();
    ui.add(root, permissionPanel, 16);
    ui.body(permissionPanel, "Allow nearby-device access to connect to your board.");
    ui.action(
        permissionPanel,
        "Allow Bluetooth access",
        R.drawable.ic_constellate_bluetooth,
        true,
        () -> {
          if (Build.VERSION.SDK_INT >= 31)
            permissions.launch(
                new String[] {
                  Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
                });
        });
    FrameLayout frame = new FrameLayout(this);
    frame.setId(R.id.sample_content_fragment);
    root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    ui.action(root, "Back to your board", R.drawable.ic_constellate_arrow, false, () -> {
      setResult(RESULT_OK);
      finish();
    });
    setContentView(root);
    showWizardIfAllowed();
  }

  private void showWizardIfAllowed() {
    boolean granted =
        Build.VERSION.SDK_INT < 31
            || (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED);
    permissionPanel.setVisibility(granted ? android.view.View.GONE : android.view.View.VISIBLE);
    if (granted
        && getSupportFragmentManager().findFragmentById(R.id.sample_content_fragment) == null)
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.sample_content_fragment,
              BluetoothChatFragment.forWord(getIntent().getStringExtra(EXTRA_WORD)))
          .commit();
  }
}
