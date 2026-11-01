package thesonid.com.brailler;

import java.util.Locale;

/** Shared input rules for the native companion and its board protocol. */
final class ConstellateRules {
  static String word(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.US);
  }

  static boolean validWord(String value) {
    return word(value).matches("[a-z]{1,8}");
  }

  static boolean validDatabaseKey(String value) {
    if (value == null || value.isEmpty() || value.length() > 128) return false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 32 || c == 127 || ".#$[]/".indexOf(c) >= 0) return false;
    }
    return true;
  }

  static boolean correctAnswer(String answer, String word) {
    return word(answer).equals(word(word));
  }

  private ConstellateRules() {}
}
