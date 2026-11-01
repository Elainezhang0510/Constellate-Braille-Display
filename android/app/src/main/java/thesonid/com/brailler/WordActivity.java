package thesonid.com.brailler;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WordActivity extends AppCompatActivity {

    private static final int MAX_WORD_LENGTH = 8;
    private static final String[] SUGGESTED = {"cat", "dog", "sun", "bed", "cup", "book"};

    private RecyclerView recyclerView;
    private WordsAdapter wordsAdapter;
    private DatabaseReference wordsReference;
    private ValueEventListener wordsListener;
    private String boardId;
    private final List<String> allWords = new ArrayList<>();
    private EditText wordEdit;
    private EditText searchEdit;
    private TextView validationView;
    private TextView braillePreview;
    private TextView headerCount;
    private LinearLayout emptyState;
    private LinearLayout suggestedChips;
    private TextToSpeech tts;
    private boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.word_layout);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.words_title);
        }

        if (!FirebaseProvider.isConfigured(this)) {
            Toast.makeText(this, R.string.login_error_firebase_not_configured, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        boardId = getIntent().getStringExtra("board");
        if (TextUtils.isEmpty(boardId)) {
            boardId = SessionStore.getBoardId(this);
        }
        if (TextUtils.isEmpty(boardId)) {
            finish();
            return;
        }

        wordsReference = FirebaseProvider.getDatabase(this)
                .getReference("Boards")
                .child(boardId)
                .child("words");

        TextView headerTitle = findViewById(R.id.words_header_title);
        headerCount = findViewById(R.id.words_header_count);
        wordEdit = findViewById(R.id.word);
        searchEdit = findViewById(R.id.words_search);
        validationView = findViewById(R.id.word_validation);
        braillePreview = findViewById(R.id.braille_preview);
        emptyState = findViewById(R.id.words_empty);
        suggestedChips = findViewById(R.id.suggested_chips);
        Button add = findViewById(R.id.add_word);
        Button speakDraft = findViewById(R.id.speak_word);
        recyclerView = findViewById(R.id.recycler_view);

        headerTitle.setText(SessionStore.getBoardName(this));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wordsAdapter = new WordsAdapter(
                this::confirmDelete,
                this::speak);
        recyclerView.setAdapter(wordsAdapter);

        setupSuggestedChips();
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady && tts != null) {
                tts.setLanguage(Locale.US);
            }
        });

        wordEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateDraftUi(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        add.setOnClickListener(v -> addWord(wordEdit.getText().toString().trim()));
        speakDraft.setOnClickListener(v -> speak(wordEdit.getText().toString().trim()));

        wordsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allWords.clear();
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    String value = child.getValue(String.class);
                    if (value != null) {
                        allWords.add(value);
                    }
                }
                Collections.sort(allWords);
                headerCount.setText(getString(R.string.home_word_count, allWords.size()));
                applyFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(WordActivity.this, R.string.words_db_error, Toast.LENGTH_SHORT).show();
            }
        };
        wordsReference.addValueEventListener(wordsListener);
    }

    private void setupSuggestedChips() {
        suggestedChips.removeAllViews();
        for (String suggestion : SUGGESTED) {
            Button chip = new Button(this);
            chip.setText(suggestion);
            chip.setAllCaps(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 8, 0);
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> addWord(suggestion));
            suggestedChips.addView(chip);
        }
    }

    private void updateDraftUi(String word) {
        if (word.isEmpty()) {
            validationView.setVisibility(View.GONE);
            braillePreview.setText("");
            return;
        }
        if (!word.matches("[a-z]+")) {
            validationView.setVisibility(View.VISIBLE);
            validationView.setText(R.string.words_invalid_chars);
            braillePreview.setText("");
            return;
        }
        if (word.length() > MAX_WORD_LENGTH) {
            validationView.setVisibility(View.VISIBLE);
            validationView.setText(R.string.words_too_long);
            braillePreview.setText("");
            return;
        }
        validationView.setVisibility(View.GONE);
        braillePreview.setText(BraillePreview.formatWord(word));
    }

    private void addWord(String word) {
        word = word == null ? "" : word.trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(word)) {
            Toast.makeText(this, R.string.words_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!word.matches("[a-z]+") || word.length() > MAX_WORD_LENGTH) {
            updateDraftUi(word);
            return;
        }
        wordsReference.child(word).setValue(word);
        wordEdit.setText("");
        braillePreview.setText("");
        Toast.makeText(this, getString(R.string.words_added, word), Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(String word) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.words_delete)
                .setMessage(getString(R.string.words_delete_confirm, word))
                .setPositiveButton(R.string.words_delete, (d, w) -> wordsReference.child(word).removeValue())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void speak(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (tts == null || !ttsReady) {
            Toast.makeText(this, R.string.words_tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "braillio_word");
    }

    private void applyFilter() {
        String query = searchEdit.getText() == null
                ? ""
                : searchEdit.getText().toString().trim().toLowerCase(Locale.US);
        List<String> filtered = new ArrayList<>();
        for (String word : allWords) {
            if (query.isEmpty() || word.contains(query)) {
                filtered.add(word);
            }
        }
        wordsAdapter.submitList(filtered);
        boolean empty = allWords.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty && query.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wordsReference != null && wordsListener != null) {
            wordsReference.removeEventListener(wordsListener);
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    interface WordActionListener {
        void onAction(String word);
    }

    static class WordsAdapter extends RecyclerView.Adapter<WordViewHolder> {

        private final List<String> words = new ArrayList<>();
        private final WordActionListener deleteListener;
        private final WordActionListener speakListener;

        WordsAdapter(WordActionListener deleteListener, WordActionListener speakListener) {
            this.deleteListener = deleteListener;
            this.speakListener = speakListener;
        }

        void submitList(List<String> newWords) {
            words.clear();
            words.addAll(newWords);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.word_card, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            holder.bind(words.get(position), deleteListener, speakListener);
        }

        @Override
        public int getItemCount() {
            return words.size();
        }
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {

        private final TextView wordText;
        private final TextView letterCount;
        private final ImageButton speakBtn;
        private final ImageButton deleteBtn;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            wordText = itemView.findViewById(R.id.word_display_recycler);
            letterCount = itemView.findViewById(R.id.word_letter_count);
            speakBtn = itemView.findViewById(R.id.word_speak_btn);
            deleteBtn = itemView.findViewById(R.id.word_delete_btn);
        }

        void bind(String word, WordActionListener deleteListener, WordActionListener speakListener) {
            wordText.setText(word);
            letterCount.setText(itemView.getContext().getString(R.string.words_letter_count, word.length()));
            speakBtn.setOnClickListener(v -> speakListener.onAction(word));
            deleteBtn.setOnClickListener(v -> deleteListener.onAction(word));
            itemView.setOnClickListener(null);
        }
    }
}
