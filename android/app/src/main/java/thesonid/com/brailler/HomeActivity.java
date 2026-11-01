package thesonid.com.brailler;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class HomeActivity extends AppCompatActivity {

    private String boardId;
    private TextView wordCountView;
    private DatabaseReference wordsRef;
    private ValueEventListener countListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionStore.isLoggedIn(this)) {
            startActivity(new Intent(this, MainActivityBraille.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_home);
        boardId = SessionStore.getBoardId(this);

        TextView nameView = findViewById(R.id.home_board_name);
        TextView idView = findViewById(R.id.home_board_id);
        wordCountView = findViewById(R.id.home_word_count);

        nameView.setText(SessionStore.getBoardName(this));
        idView.setText(getString(R.string.home_board_id_label, boardId));
        wordCountView.setText(getString(R.string.home_word_count, 0));

        findViewById(R.id.home_words).setOnClickListener(v -> {
            Intent intent = new Intent(this, WordActivity.class);
            intent.putExtra("board", boardId);
            startActivity(intent);
        });
        findViewById(R.id.home_wifi).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.home_settings).setOnClickListener(v ->
                startActivity(new Intent(this, BoardSettingsActivity.class)));
        findViewById(R.id.home_help).setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));

        if (FirebaseProvider.isConfigured(this)) {
            wordsRef = FirebaseProvider.getDatabase(this)
                    .getReference("Boards")
                    .child(boardId)
                    .child("words");
            countListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    wordCountView.setText(getString(R.string.home_word_count, snapshot.getChildrenCount()));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(HomeActivity.this, R.string.words_db_error, Toast.LENGTH_SHORT).show();
                }
            };
            wordsRef.addValueEventListener(countListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView nameView = findViewById(R.id.home_board_name);
        if (nameView != null) {
            nameView.setText(SessionStore.getBoardName(this));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wordsRef != null && countListener != null) {
            wordsRef.removeEventListener(countListener);
        }
    }
}
