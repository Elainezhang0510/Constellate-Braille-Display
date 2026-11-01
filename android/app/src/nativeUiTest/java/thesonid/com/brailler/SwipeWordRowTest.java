package thesonid.com.brailler;

import static org.junit.Assert.*;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SwipeWordRowTest {
  @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void blankSpaceButtonsAndExposedDeleteCanSwipeBackWithoutDeleting() {
    try(var controller=Robolectric.buildActivity(AssistantLibraryTest.Host.class).setup()){
    ConstellateUi ui=new ConstellateUi(RuntimeEnvironment.getApplication(),false,false);
    LinearLayout parent=ui.column(),card=ui.row();
    card.setFocusable(true);
    Button action=new Button(ui.context);action.setText("Speak");
    LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(ui.dp(70),ui.dp(60));
    buttonParams.setMarginStart(ui.dp(220));card.addView(action,buttonParams);
    int[] deletes={0},speaks={0};action.setOnClickListener(v->speaks[0]++);
    SwipeWordRow row=new SwipeWordRow(ui,card,"cat",()->deletes[0]++);
    parent.addView(row);
    controller.get().setContentView(parent);
    parent.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(360),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ui.dp(100),View.MeasureSpec.EXACTLY));
    parent.layout(0,0,ui.dp(360),ui.dp(100));
    for(int closeFrom:new int[]{180,330,30}){
      swipe(row,ui.dp(40),ui.dp(160));
      assertEquals(ui.dp(92),card.getTranslationX(),.1f);
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,row.getChildAt(0).getImportantForAccessibility());
      swipe(row,ui.dp(closeFrom),ui.dp(closeFrom-120));
      assertEquals(0,card.getTranslationX(),.1f);
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,row.getChildAt(0).getImportantForAccessibility());
    }
    assertEquals(0,deletes[0]);assertEquals(0,speaks[0]);
    swipe(row,ui.dp(40),ui.dp(160));
    event(row,MotionEvent.ACTION_DOWN,ui.dp(25));event(row,MotionEvent.ACTION_UP,ui.dp(25));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(1,deletes[0]);
    }
  }
  private static void swipe(View row,float from,float to){
    event(row,MotionEvent.ACTION_DOWN,from);
    event(row,MotionEvent.ACTION_MOVE,(from+to)/2);
    event(row,MotionEvent.ACTION_MOVE,to);
    event(row,MotionEvent.ACTION_UP,to);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));
  }
  private static void event(View row,int action,float x){
    long now=android.os.SystemClock.uptimeMillis();
    MotionEvent e=MotionEvent.obtain(now,now,action,x,25,0);
    row.dispatchTouchEvent(e);e.recycle();
  }
}
