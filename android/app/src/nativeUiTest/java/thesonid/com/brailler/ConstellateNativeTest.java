package thesonid.com.brailler;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
/** Optional native runtime checks: gradlew testDebugUnitTest -PnativeUiTests. */
public class ConstellateNativeTest {
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void loginScreenRendersAsNativeAndroidViews() throws Exception {
    RuntimeEnvironment.setQualifiers("w412dp-h892dp-xhdpi");
    try (org.robolectric.android.controller.ActivityController<MainActivityBraille> controller =
        Robolectric.buildActivity(MainActivityBraille.class).setup()) {
      MainActivityBraille activity = controller.get();
      View root = activity.findViewById(R.id.constellate_root);
      int width = 824, height = 1784;
      root.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, width, height);
      android.graphics.Bitmap bitmap =
          android.graphics.Bitmap.createBitmap(
              width, height, android.graphics.Bitmap.Config.ARGB_8888);
      root.draw(new android.graphics.Canvas(bitmap));
      java.io.File directory = new java.io.File("build/constellate-preview");
      directory.mkdirs();
      try (java.io.FileOutputStream stream =
          new java.io.FileOutputStream(new java.io.File(directory, "login.png"))) {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
      }
      assertEquals(View.GONE, activity.findViewById(R.id.constellate_navigation).getVisibility());
    }
  }

  @Test
  public void localWordsSurviveReopeningWithoutLeakingToAnotherAccount() {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("constellate_library_test-one", 0).edit().clear().commit();
    context.getSharedPreferences("constellate_library_test-two", 0).edit().clear().commit();
    ConstellateWords.Observer observer =
        new ConstellateWords.Observer() {
          public void changed() {}

          public void failed(String message) {
            fail(message);
          }
        };
    ConstellateWords first = new ConstellateWords(context, "test-one", observer);
    first.write("engineer", false, success -> assertTrue(success));
    first.close();
    ConstellateWords reopened = new ConstellateWords(context, "test-one", observer);
    ConstellateWords other = new ConstellateWords(context, "test-two", observer);
    assertTrue(reopened.words.contains("engineer"));
    assertTrue(other.words.isEmpty());
    reopened.write("engineer", true, success -> assertTrue(success));
    assertTrue(new ConstellateWords(context, "test-one", observer).words.isEmpty());
    reopened.close();
    other.close();
  }

  @Test
  public void eightLetterBrailleWrapsOnNarrowPhonesAndQuizDoesNotRevealAnswer() {
    ConstellateUi ui = new ConstellateUi(RuntimeEnvironment.getApplication(), false, false);
    BrailleCellsView wide = new BrailleCellsView(ui, "engineer", false);
    BrailleCellsView narrow = new BrailleCellsView(ui, "engineer", true);
    wide.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(320), View.MeasureSpec.EXACTLY), 0);
    narrow.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(160), View.MeasureSpec.EXACTLY), 0);
    assertTrue(narrow.getMeasuredHeight() > wide.getMeasuredHeight());
    assertFalse(narrow.getContentDescription().toString().contains("engineer"));
    assertTrue(wide.getContentDescription().toString().contains("engineer"));
  }

  @Test
  public void userModeForcesHighContrastAndNativeActionsRemainUsable() {
    ConstellateUi ui = new ConstellateUi(RuntimeEnvironment.getApplication(), false, true);
    assertTrue(ui.dark);
    assertEquals(0xFF000000, ui.bg);
    assertEquals(0xFFFFFFFF, ui.ink);
    LinearLayout column = ui.column();
    boolean[] clicked = {false};
    View button =
        ui.action(
            column, "Speak word", R.drawable.ic_constellate_volume, true, () -> clicked[0] = true);
    assertTrue(button.getMinimumHeight() >= ui.dp(72));
    button.performClick();
    assertTrue(clicked[0]);
  }
}
