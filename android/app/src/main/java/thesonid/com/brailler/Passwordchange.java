package thesonid.com.brailler;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import java.util.HashMap;
import java.util.Map;

/** Device password management, separate from the account email password. */
public class Passwordchange extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle state) {
    setTheme(R.style.ConstellateTheme);
    super.onCreate(state);
    if (!FirebaseProvider.isConfigured(this)
        || FirebaseAuth.getInstance().getCurrentUser() == null) {
      startActivity(new Intent(this, MainActivityBraille.class));
      finish();
      return;
    }
    ConstellateUi ui = ConstellateUi.forAccount(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout root = ui.column();
    ui.padding(root, 18, 28);
    root.setBackgroundColor(ui.bg);
    scroll.addView(root);
    setContentView(scroll);
    ui.action(root, "Back to settings", R.drawable.ic_constellate_arrow, false, this::finish);
    ui.gap(root, 24);
    ui.title(root, "Device password", 30);
    ui.gap(root, 20);
    LinearLayout card = ui.panel(root);
    ui.body(card, "This changes the board password, not your email account password.");
    EditText board = ui.field(card, "Board ID", "Enter the board ID", InputType.TYPE_CLASS_TEXT);
    String current = SessionStore.getBoardId(this);
    if (current != null) board.setText(current);
    EditText old =
        ui.field(
            card,
            "Current device password",
            "Current password",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditText next =
        ui.field(
            card,
            "New device password",
            "New password",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    old.setSaveEnabled(false);
    next.setSaveEnabled(false);
    LinearLayout save =
        ui.action(card, "Save device password", R.drawable.ic_constellate_check, true, () -> {});
    save.setOnClickListener(
        v -> {
          if (!save.isEnabled()) return;
          String id = board.getText().toString().trim(),
              before = old.getText().toString(),
              after = next.getText().toString();
          if (!ConstellateRules.validDatabaseKey(id)) {
            board.setError("Enter a valid board ID.");
            return;
          }
          if (!ConstellateRules.validDatabaseKey(before)) {
            old.setError("Enter a valid current password.");
            return;
          }
          if (!ConstellateRules.validDatabaseKey(after)) {
            next.setError("Use 1–128 characters without . # $ [ ] / or control characters.");
            return;
          }
          if (before.equals(after)) {
            next.setError("Choose a different password.");
            return;
          }
          ui.enable(save, false);
          DatabaseReference passwords =
              FirebaseProvider.getDatabase(this).getReference("Boards").child(id).child("password");
          passwords
              .child(before)
              .get()
              .addOnCompleteListener(
                  check -> {
                    if (isDestroyed()) return;
                    if (!check.isSuccessful() || !check.getResult().exists()) {
                      ui.enable(save, true);
                      old.setError("Unable to verify the board and current password.");
                      return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put(before, null);
                    update.put(after, after);
                    passwords
                        .updateChildren(update)
                        .addOnCompleteListener(
                            result -> {
                              if (isDestroyed()) return;
                              ui.enable(save, true);
                              if (result.isSuccessful()) {
                                old.setText("");
                                next.setText("");
                                Toast.makeText(this, "Device password changed.", Toast.LENGTH_LONG)
                                    .show();
                                finish();
                              } else
                                Toast.makeText(
                                        this,
                                        "Could not save the password. Please try again.",
                                        Toast.LENGTH_LONG)
                                    .show();
                            });
                  });
        });
  }
}
