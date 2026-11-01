package thesonid.com.brailler;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class BoardSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_board_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        String boardId = SessionStore.getBoardId(this);
        EditText nameEdit = findViewById(R.id.settings_board_name);
        TextView idView = findViewById(R.id.settings_board_id);

        nameEdit.setText(SessionStore.getBoardName(this));
        idView.setText(getString(R.string.home_board_id_label, boardId == null ? "—" : boardId));

        findViewById(R.id.settings_save_name).setOnClickListener(v -> {
            String name = nameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, R.string.settings_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            SessionStore.setBoardName(this, name);
            Toast.makeText(this, R.string.settings_name_saved, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.settings_change_password).setOnClickListener(v ->
                startActivity(new Intent(this, Passwordchange.class)));

        findViewById(R.id.settings_wifi).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        findViewById(R.id.settings_help).setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));

        findViewById(R.id.settings_logout).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_logout)
                .setMessage(R.string.settings_logout_confirm)
                .setPositiveButton(R.string.settings_logout, (d, w) -> {
                    SessionStore.clearSession(this);
                    Intent intent = new Intent(this, MainActivityBraille.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
