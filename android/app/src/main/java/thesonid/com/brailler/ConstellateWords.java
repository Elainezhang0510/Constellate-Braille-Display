package thesonid.com.brailler;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.util.*;

/** Local, per-account practice library or the existing board's realtime word list. */
final class ConstellateWords {
  interface Observer {
    void changed();

    void failed(String message);
  }

  interface Completion {
    void done(boolean success);
  }

  final List<String> words = new ArrayList<>();
  private final SharedPreferences local;
  private final Observer observer;
  private DatabaseReference reference;
  private ValueEventListener listener;
  private boolean closed;
  String error;
  boolean loaded, cloud;

  ConstellateWords(Context context, String uid, Observer observer) {
    local = context.getSharedPreferences("constellate_library_" + uid, Context.MODE_PRIVATE);
    this.observer = observer;
    String board = SessionStore.getBoardId(context);
    cloud = board != null;
    if (!cloud) {
      words.addAll(local.getStringSet("words", Collections.emptySet()));
      Collections.sort(words);
      loaded = true;
      return;
    }
    reference =
        FirebaseProvider.getDatabase(context).getReference("Boards").child(board).child("words");
    listener =
        new ValueEventListener() {
          public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (closed) return;
            error = null;
            words.clear();
            for (DataSnapshot child : snapshot.getChildren()) {
              Object value = child.getValue();
              if (value instanceof String && ConstellateRules.validWord((String) value))
                words.add((String) value);
            }
            Collections.sort(words);
            loaded = true;
            observer.changed();
          }

          public void onCancelled(@NonNull DatabaseError error) {
            if (closed) return;
            loaded = false;
            ConstellateWords.this.error =
                "Unable to load board words. Check your connection and board access.";
            observer.failed(ConstellateWords.this.error);
          }
        };
    reference.addValueEventListener(listener);
  }

  void write(String word, boolean remove, Completion completion) {
    if (!ConstellateRules.validWord(word)) {
      completion.done(false);
      return;
    }
    if (reference == null) {
      if (remove) words.remove(word);
      else if (!words.contains(word)) words.add(word);
      Collections.sort(words);
      local.edit().putStringSet("words", new HashSet<>(words)).apply();
      observer.changed();
      completion.done(true);
    } else {
      reference
          .child(word)
          .setValue(remove ? null : word)
          .addOnCompleteListener(
              task -> {
                if (!closed) completion.done(task.isSuccessful());
              });
    }
  }

  void close() {
    closed = true;
    if (reference != null && listener != null) reference.removeEventListener(listener);
  }
}
