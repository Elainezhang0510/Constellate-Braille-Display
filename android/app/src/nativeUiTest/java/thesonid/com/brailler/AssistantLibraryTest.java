package thesonid.com.brailler;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AssistantLibraryTest {
  @Test public void legacyWordsMigrateOnceAndBooksAreAccountAndBoardScoped() {
    Context c=RuntimeEnvironment.getApplication();String uid=UUID.randomUUID().toString();
    WordBooks books=new WordBooks(c,uid,null);
    books.migrate(Arrays.asList("cat","sun"),false);assertTrue(books.books.isEmpty());
    books.migrate(Arrays.asList("cat","sun"),true);assertEquals(1,books.books.size());
    WordBooks.Book first=books.books.get(0),second=books.create("School");books.add(second,"cat");
    assertTrue(books.usedElsewhere(first,"cat"));books.remove(first,"cat");
    WordBooks reopened=new WordBooks(c,uid,null);
    assertEquals(Arrays.asList("sun"),reopened.books.get(0).words);
    assertEquals(Arrays.asList("cat"),reopened.books.get(1).words);
    reopened.migrate(Arrays.asList("cat","sun"),true);assertEquals(2,reopened.books.size());
    assertTrue(new WordBooks(c,uid,"another-board").books.isEmpty());
    assertTrue(new WordBooks(c,uid+"-other",null).books.isEmpty());
  }
  @Test public void creatingBookBeforeCloudLoadDoesNotLoseExistingWords() {
    WordBooks books=new WordBooks(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString(),null);
    WordBooks.Book created=books.create("New book");books.add(created,"cat");
    books.migrate(Arrays.asList("cat","sun"),true);
    assertEquals(2,books.books.size());assertEquals(Arrays.asList("sun"),books.books.get(1).words);
  }
  @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void nativeBookFlowPreservesWordsAndRestoresLocation() throws Exception {
    RuntimeEnvironment.setQualifiers("w390dp-h844dp-mdpi");
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();ConstellateUi ui=new ConstellateUi(activity,false,false);
      SessionStore.clearSession(activity);String uid=UUID.randomUUID().toString();
      ConstellateWords words=new ConstellateWords(activity,uid,new ConstellateWords.Observer(){public void changed(){}public void failed(String s){fail(s);}});
      words.write("cat",false,ok->assertTrue(ok));
      WordBooks books=new WordBooks(activity,uid,null);books.migrate(words.words,true);
      WordBooks.Book school=books.create("School");books.add(school,"cat");
      LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);ui.padding(root,18,16);activity.setContentView(root);
      String[] sent={null};AssistantLibrary.Host host=new AssistantLibrary.Host(){public void speak(String s){}public void stopSpeech(){}public void hideKeyboard(){}public void send(String s){sent[0]=s;}};
      AssistantLibrary screen=new AssistantLibrary(activity,host);screen.bind(root,ui,words,uid);
      assertNotNull(findText(root,"Word Library"));assertNotNull(findDescription(root,"Starter guide"));
      findDescription(root,"Everyday, 1 words").performClick();
      assertNotNull(findText(root,"Everyday"));
      EditText input=(EditText)findDescription(root,"Add a word");input.setText("sun");findText(root,"Add word").performClick();
      assertTrue(words.words.contains("sun"));assertNotNull(findDescription(root,"View sun in braille"));
      findDescription(root,"Send cat to braille display").performClick();assertEquals("cat",sent[0]);
      findDescription(root,"View cat in braille").performClick();assertNotNull(findText(root,"Braille display"));
      Bundle saved=new Bundle();screen.save(saved);screen.stop();
      AssistantLibrary restored=new AssistantLibrary(activity,host);restored.restore(saved);restored.bind(root,ui,words,uid);assertNotNull(findText(root,"Braille display"));
      assertTrue(restored.back());findDescription(root,"Delete cat").performClick();
      assertTrue("Other book still needs cat",words.words.contains("cat"));
      assertNull(findDescription(root,"View cat in braille"));
      findDescription(root,"Delete sun").performClick();assertFalse(words.words.contains("sun"));
      assertTrue(restored.back());assertFalse(restored.back());
      findDescription(root,"New word book").performClick();
      android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      EditText bookName=(EditText)findDescription(dialog.getWindow().getDecorView(),"Book name");bookName.setText("Personal");
      findText(dialog.getWindow().getDecorView(),"Create book").performClick();assertNotNull(findText(root,"Personal"));
      assertTrue(restored.back());findDescription(root,"School, 1 words").performClick();assertNotNull(findDescription(root,"View cat in braille"));
      assertTrue(restored.back());
      View schoolTile=findDescription(root,"School, 1 words");assertTrue(schoolTile.performLongClick());
      androidx.appcompat.app.AlertDialog confirmation=org.robolectric.util.ReflectionHelpers.getField(restored,"deleteDialog");
      assertNotNull(findText(confirmation.getWindow().getDecorView(),"delete School?"));
      captureSized(confirmation.getWindow().getDecorView(),"delete-wordbook",340,210);
      findText(confirmation.getWindow().getDecorView(),"No").performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertNotNull(findDescription(root,"School, 1 words"));
      findDescription(root,"School, 1 words").performLongClick();
      confirmation=org.robolectric.util.ReflectionHelpers.getField(restored,"deleteDialog");
      findText(confirmation.getWindow().getDecorView(),"Yes").performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertNull(findDescription(root,"School, 1 words"));
      assertTrue(words.words.contains("cat"));
      assertNull(new WordBooks(activity,uid,null).find(school.id));
      findDescription(root,"Starter guide").performClick();
      for(int stage=0;stage<8;stage++){
        org.robolectric.util.ReflectionHelpers.setField(restored,"guideStage",stage);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(restored,"render");
        assertNotNull("Pointer for guide step "+stage,root.findViewWithTag("starter_guide_pointer"));
        if(stage==0||stage==2)capture(root,"starter-guide-"+stage);
        if(stage==2){
          View bar=(View)findDescription(root,"Add a word").getParent();
          View card=findDescription(root,"cat, 3 letters, Grade 1");
          android.graphics.Rect a=new android.graphics.Rect(),b=new android.graphics.Rect();
          bar.getDrawingRect(a);root.offsetDescendantRectToMyCoords(bar,a);
          card.getDrawingRect(b);root.offsetDescendantRectToMyCoords(card,b);
          assertEquals(a.left,b.left);assertEquals(a.right,b.right);
          View speak=findDescription(root,"Speak cat"),send=findDescription(root,"Send cat to braille display"),dots=findDescription(root,"View cat in braille");
          assertEquals(send.getLeft()-speak.getRight(),dots.getLeft()-send.getRight());
          assertTrue(send.getLeft()-speak.getRight()>ui.dp(4));
        }
      }
      assertTrue(restored.back());
      findDescription(root,"Everyday, 0 words").performClick();
      ((EditText)findDescription(root,"Add a word")).setText("cat");findText(root,"Add word").performClick();
      root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(748,View.MeasureSpec.EXACTLY));root.layout(0,0,390,748);
      android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(390,748,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
      java.io.File out=new java.io.File("build/constellate-preview/assistant-wordbook.png");out.getParentFile().mkdirs();try(var stream=new java.io.FileOutputStream(out)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
      restored.search("cat");assertNotNull(findText(root,"Search results"));
      assertNotNull(findDescription(root,"Send cat to braille display"));
      assertNull(findDescription(root,"Delete cat"));
      findDescription(root,"View cat in braille").performClick();assertNotNull(findText(root,"Braille display"));
      assertTrue(restored.back());assertNotNull(findText(root,"Search results"));
      restored.search("zebra");assertNotNull(findText(root,"No matching words. Try another word."));
      assertTrue(restored.back());assertNotNull(findDescription(root,"Starter guide"));
      restored.stop();words.close();
    }
  }
  @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void onlyAssistantUsesBooksAndTheExistingFiveTabNavigationIsKept() throws Exception {
    try(var controller=Robolectric.buildActivity(MainActivityBraille.class).setup()){
      MainActivityBraille activity=controller.get();
      LinearLayout content=activity.findViewById(R.id.constellate_content);
      SessionStore.clearSession(activity);
      ConstellateWords words=new ConstellateWords(activity,UUID.randomUUID().toString(),new ConstellateWords.Observer(){public void changed(){}public void failed(String s){fail(s);}});
      org.robolectric.util.ReflectionHelpers.setField(activity,"library",words);
      org.robolectric.util.ReflectionHelpers.setField(activity,"userMode",true);
      org.robolectric.util.ReflectionHelpers.setField(activity,"ui",new ConstellateUi(activity,false,true));
      content.removeAllViews();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"libraryScreen");
      assertNotNull(findText(content,"Search words"));assertNull(findDescription(content,"Starter guide"));
      org.robolectric.util.ReflectionHelpers.setField(activity,"userMode",false);
      org.robolectric.util.ReflectionHelpers.setField(activity,"ui",new ConstellateUi(activity,false,false));
      content.removeAllViews();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"libraryScreen");
      assertNotNull(findDescription(content,"Starter guide"));assertNull(findText(content,"Search words"));
      LinearLayout nav=activity.findViewById(R.id.constellate_navigation);nav.removeAllViews();
      org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"bottomNavigation");
      assertNotNull(findDescription(nav,"Your board"));assertNotNull(findDescription(nav,"Word library"));
      assertNotNull(findDescription(nav,"Practice"));assertNotNull(findDescription(nav,"Settings"));assertNotNull(findDescription(nav,"Help"));
      assertEquals(5,((ViewGroup)nav.getChildAt(1)).getChildCount());
      for(boolean user:new boolean[]{false,true}){
        org.robolectric.util.ReflectionHelpers.setField(activity,"ui",new ConstellateUi(activity,false,user));
        nav.removeAllViews();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"bottomNavigation");
        nav.setVisibility(View.VISIBLE);
        for(int width:new int[]{320,390,412}){
          nav.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));nav.layout(0,0,width,nav.getMeasuredHeight());
          ViewGroup tabs=(ViewGroup)nav.getChildAt(1);int iconTop=-1,labelTop=-1;
          for(int i=0;i<5;i++){
            ViewGroup tab=(ViewGroup)tabs.getChildAt(i);View icon=tab.getChildAt(0);TextView label=(TextView)tab.getChildAt(1);
            if(i==0){iconTop=icon.getTop();labelTop=label.getTop();}
            assertEquals(iconTop,icon.getTop());assertEquals(labelTop,label.getTop());assertEquals(1,label.getLineCount());
          }
          if(width==390)captureSized(nav,"navigation-"+(user?"user":"assistant"),width,nav.getHeight());
        }
      }
      words.close();
    }
  }
  private static void capture(View root,String name)throws Exception{
    captureSized(root,name,390,748);
  }
  private static void captureSized(View root,String name,int width,int height)throws Exception{
    root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
    java.io.File out=new java.io.File("build/constellate-preview/"+name+".png");out.getParentFile().mkdirs();try(var stream=new java.io.FileOutputStream(out)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
  }
  static View findDescription(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findDescription(g.getChildAt(i),s);if(found!=null)return found;}}return null;}
  static TextView findText(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView found=findText(g.getChildAt(i),s);if(found!=null)return found;}}return null;}
  public static class Host extends AppCompatActivity { @Override protected void onCreate(Bundle b){setTheme(R.style.ConstellateTheme);super.onCreate(b);} }
}
