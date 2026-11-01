package thesonid.com.brailler;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.speech.*;
import androidx.appcompat.app.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VoiceSearchTest {
  @Test public void systemSpeechActivityRemainsTheFirstChoice(){
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();VoiceSearch voice=new VoiceSearch(activity,text->fail());
      voice.start(73);
      assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,activity.request.getAction());
      assertEquals("en-US",activity.request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
      assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());voice.stop();
    }
  }
  @Test public void missingSpeechActivityFallsBackToServiceAfterMicrophoneGrant(){
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();activity.externalAvailable=false;service(activity);
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);
      String[] result={null};VoiceSearch voice=new VoiceSearch(activity,text->result[0]=text);
      voice.start(73);assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      assertEquals(VoiceSearch.MICROPHONE_PERMISSION,Shadows.shadowOf(activity).getLastRequestedPermission().requestCode);
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
      voice.permissionResult(true);Shadows.shadowOf(Looper.getMainLooper()).idle();
      SpeechRecognizer recognizer=ShadowSpeechRecognizer.getLatestSpeechRecognizer();assertNotNull(recognizer);
      ShadowSpeechRecognizer shadow=Shadows.shadowOf(recognizer);
      assertEquals("en-US",shadow.getLastRecognizerIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
      Bundle values=new Bundle();values.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList("cat")));
      shadow.triggerOnResults(values);assertEquals("cat",result[0]);assertTrue(shadow.isDestroyed());
    }
  }
  @Test public void errorsCanRetryAndStoppingIgnoresLateRecognition(){
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();activity.externalAvailable=false;service(activity);
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
      String[] result={null};VoiceSearch voice=new VoiceSearch(activity,text->result[0]=text);
      voice.start(73);Shadows.shadowOf(Looper.getMainLooper()).idle();
      ShadowSpeechRecognizer first=Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      first.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH);
      AlertDialog dialog=org.robolectric.util.ReflectionHelpers.getField(voice,"dialog");
      assertNotNull(AssistantLibraryTest.findText(dialog.getWindow().getDecorView(),"No word heard. Tap Try again and say an English word."));
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertTrue(first.isDestroyed());
      ShadowSpeechRecognizer second=Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      voice.stop();assertTrue(second.isDestroyed());
      Bundle values=new Bundle();values.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList("cat")));
      second.triggerOnResults(values);assertNull(result[0]);
    }
  }
  @Test public void missingSystemEngineUsesBundledOfflineRecognizerAndIgnoresCancelledResults(){
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();activity.externalAvailable=false;
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
      OfflineSpeech.Callback[] callback={null};boolean[] cancelled={false};String[] result={null};
      VoiceSearch voice=new VoiceSearch(activity,text->result[0]=text,(context,listener)->{callback[0]=listener;return ()->cancelled[0]=true;});
      voice.start(73);assertNotNull(callback[0]);assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      callback[0].ready();
      AlertDialog dialog=org.robolectric.util.ReflectionHelpers.getField(voice,"dialog");
      assertNotNull(AssistantLibraryTest.findText(dialog.getWindow().getDecorView(),"Listening… Say an English word."));
      callback[0].text("cat");assertEquals("cat",result[0]);assertTrue(cancelled[0]);
      result[0]=null;voice.start(73);voice.stop();callback[0].text("dog");assertNull(result[0]);
    }
  }
  @Test public void permissionDenialDoesNotStartOfflineRecording(){
    try(var controller=Robolectric.buildActivity(Host.class).setup()){
      Host activity=controller.get();activity.externalAvailable=false;
      VoiceSearch voice=new VoiceSearch(activity,text->fail(),(context,listener)->{fail("No recording without permission");return ()->{};});
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);
      voice.start(73);voice.permissionResult(false);
      android.app.Dialog dialog=ShadowDialog.getLatestDialog();
      assertNotNull(AssistantLibraryTest.findText(dialog.getWindow().getDecorView(),"Allow microphone access"));
      assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());voice.stop();
    }
  }
  private static void service(Host activity){
    ResolveInfo info=new ResolveInfo();info.serviceInfo=new ServiceInfo();
    info.serviceInfo.name="TestRecognizer";info.serviceInfo.packageName=activity.getPackageName();
    info.serviceInfo.applicationInfo=activity.getApplicationInfo();
    Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(new Intent(RecognitionService.SERVICE_INTERFACE),info);
  }
  public static class Host extends AppCompatActivity {
    boolean externalAvailable=true;Intent request;
    @Override protected void onCreate(Bundle state){setTheme(R.style.ConstellateTheme);super.onCreate(state);}
    @Override public void startActivityForResult(Intent intent,int requestCode){
      request=intent;if(!externalAvailable)throw new ActivityNotFoundException();
    }
  }
}
