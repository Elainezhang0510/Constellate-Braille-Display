package thesonid.com.brailler;

import static org.junit.Assert.*;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VoiceWordLibraryTest {
  private ConstellateWords prepare(ConstellateActivity activity, boolean user) {
    SessionStore.clearSession(activity);
    String uid = UUID.randomUUID().toString();
    ConstellateWords words = new ConstellateWords(activity, uid, activity);
    ReflectionHelpers.setField(activity, "accountUid", uid);
    ReflectionHelpers.setField(activity, "library", words);
    ReflectionHelpers.setField(activity, "page", "library");
    ReflectionHelpers.setField(activity, "userMode", user);
    ReflectionHelpers.setField(activity, "ui", new ConstellateUi(activity, false, user));
    LinearLayout content = activity.findViewById(R.id.constellate_content);
    content.removeAllViews();
    ReflectionHelpers.callInstanceMethod(activity, "libraryScreen");
    return words;
  }
  private void result(ConstellateActivity activity, ConstellateWords words, String value) {
    ReflectionHelpers.setField(activity, "pendingVoiceWord", value);
    ReflectionHelpers.setField(activity, "pendingVoiceLibrary", words);
    ReflectionHelpers.callInstanceMethod(activity, "checkVoiceSearchResult");
  }
  private AlertDialog dialog(ConstellateActivity activity) {
    return ReflectionHelpers.getField(activity, "voiceWordDialog");
  }
  @Test public void missingVoiceWordRequiresConfirmationAndPersistsInBothExperiences() {
    for (boolean user : new boolean[]{true, false}) {
      try (var controller = Robolectric.buildActivity(MainActivityBraille.class).setup()) {
        ConstellateActivity activity = controller.get();
        ConstellateWords words = prepare(activity, user);
        result(activity, words, "otter");
        assertNotNull(dialog(activity));
        assertFalse(words.words.contains("otter"));
        AssistantLibraryTest.findDescription(dialog(activity).getWindow().getDecorView(), "Cancel").performClick();
        assertFalse(words.words.contains("otter"));
        result(activity, words, "otter");
        AssistantLibraryTest.findDescription(dialog(activity).getWindow().getDecorView(), "Add word").performClick();
        assertTrue(words.words.contains("otter"));
        String uid = ReflectionHelpers.getField(activity, "accountUid");
        assertTrue(new ConstellateWords(activity, uid, activity).words.contains("otter"));
        if (!user) {
          WordBooks books = new WordBooks(activity, uid, null);
          assertTrue(books.books.stream().anyMatch(book -> book.words.contains("otter")));
        }
      }
    }
  }
  @Test public void loadingMatchingAndInvalidResultsDoNotOfferToAdd() {
    try (var controller = Robolectric.buildActivity(MainActivityBraille.class).setup()) {
      ConstellateActivity activity = controller.get();
      ConstellateWords words = prepare(activity, true);
      words.loaded = false;
      result(activity, words, "cat");
      assertNull(dialog(activity));
      words.words.add("cat");words.loaded = true;activity.changed();
      assertNull(dialog(activity));
      result(activity, words, "ca");assertNull(dialog(activity));
      result(activity, words, "two words");assertNull(dialog(activity));
      result(activity, words, "elephants");assertNull(dialog(activity));
      words.loaded = false;result(activity, words, "dog");
      words.loaded = true;activity.changed();assertNotNull(dialog(activity));
    }
  }
  @Test public void userWordRowSendsToBluetoothConnectionPageWhenDisconnected() {
    BluetoothSession.disconnect();
    try (var controller = Robolectric.buildActivity(MainActivityBraille.class).setup()) {
      ConstellateActivity activity = controller.get();
      ConstellateWords words = prepare(activity, true);
      words.write("cat", false, ok -> assertTrue(ok));
      View root = activity.findViewById(R.id.constellate_content);
      assertNull(AssistantLibraryTest.findDescription(root, "Speak word cat"));
      View send = AssistantLibraryTest.findDescription(root, "Send cat to braille display");
      assertNotNull(send);send.performClick();
      Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
      assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
      assertEquals("cat", intent.getStringExtra(MainActivity.EXTRA_WORD));
    }
  }
}
