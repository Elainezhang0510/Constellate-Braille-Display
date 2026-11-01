package thesonid.com.brailler;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Book membership is local to an account and board; the existing word library remains intact. */
final class WordBooks {
  static final class Book {
    final String id, name;
    final List<String> words = new ArrayList<>();
    Book(String id, String name) { this.id = id; this.name = name; }
  }
  final List<Book> books = new ArrayList<>();
  private final SharedPreferences preferences;
  private boolean migrated;

  WordBooks(Context context, String uid, String board) {
    preferences = context.getSharedPreferences("constellate_books_" + uid + "_" + (board == null ? "local" : board), 0);
    migrated = preferences.getBoolean("migrated", false);
    try {
      JSONArray data = new JSONArray(preferences.getString("books", "[]"));
      for (int i = 0; i < data.length(); i++) {
        JSONObject item = data.getJSONObject(i);
        Book book = new Book(item.getString("id"), item.getString("name"));
        JSONArray words = item.getJSONArray("words");
        for (int j = 0; j < words.length(); j++) {
          String word = words.getString(j);
          if (ConstellateRules.validWord(word) && !book.words.contains(word)) book.words.add(word);
        }
        books.add(book);
      }
    } catch (JSONException ignored) { /* Keep the source word library available for migration. */ }
  }

  void migrate(List<String> existing, boolean loaded) {
    if (migrated || !loaded) return;
    List<String> unassigned = new ArrayList<>();
    for (String word : existing) {
      boolean assigned = false;
      for (Book book : books) if (book.words.contains(word)) { assigned = true; break; }
      if (!assigned) unassigned.add(word);
    }
    if (!unassigned.isEmpty()) {
      Book book = create("Everyday");
      book.words.addAll(unassigned);
    }
    migrated = true;
    save();
  }
  Book create(String name) {
    Book book = new Book(UUID.randomUUID().toString(), name.trim());
    books.add(book); save(); return book;
  }
  Book find(String id) {
    for (Book book : books) if (book.id.equals(id)) return book;
    return null;
  }
  void add(Book book, String word) {
    if (!book.words.contains(word)) { book.words.add(word); save(); }
  }
  boolean usedElsewhere(Book selected, String word) {
    for (Book book : books) if (book != selected && book.words.contains(word)) return true;
    return false;
  }
  void remove(Book book, String word) { book.words.remove(word); save(); }
  void delete(Book book) { books.remove(book); save(); }
  private void save() {
    JSONArray data = new JSONArray();
    try {
      for (Book book : books) data.put(new JSONObject().put("id", book.id).put("name", book.name).put("words", new JSONArray(book.words)));
      preferences.edit().putString("books", data.toString()).putBoolean("migrated", migrated).apply();
    } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
  }
}
