package thesonid.com.brailler;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.*;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;

/** Uses a system speech activity or, when absent, the configured recognition service. */
final class VoiceSearch {
  static final int MICROPHONE_PERMISSION = 75;
  interface Result { void recognized(String text); }
  private final AppCompatActivity activity;
  private final Result result;
  private SpeechRecognizer recognizer;
  private AlertDialog dialog;
  private TextView status;
  private boolean listening;
  private boolean offline;
  private OfflineSpeech.Session offlineSession;
  private final OfflineSpeech.Factory offlineFactory;
  private int generation;

  VoiceSearch(AppCompatActivity activity, Result result) {
    this(activity,result,OfflineSpeech::start);
  }
  VoiceSearch(AppCompatActivity activity, Result result,OfflineSpeech.Factory offlineFactory) {
    this.activity=activity;this.result=result;this.offlineFactory=offlineFactory;
  }

  static Intent request() {
    return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)
        .putExtra(RecognizerIntent.EXTRA_PROMPT,"Say a word to search your library");
  }

  void start(int requestCode) {
    stop();
    try {
      activity.startActivityForResult(request(),requestCode);
    } catch(ActivityNotFoundException|SecurityException error) {
      startService();
    }
  }

  void startService() {
    offline=!SpeechRecognizer.isRecognitionAvailable(activity);
    if(ContextCompat.checkSelfPermission(activity,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);
      return;
    }
    ConstellateUi ui=ConstellateUi.forAccount(activity);
    android.widget.LinearLayout body=ui.column();ui.padding(body,24,24);
    ui.title(body,"Voice search",24);
    status=ui.body(body,"Say an English word…");
    status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    AlertDialog created=new AlertDialog.Builder(activity).setView(body)
        .setNegativeButton("Cancel",(d,w)->stop())
        .setPositiveButton("Try again",(d,w)->{}).create();
    dialog=created;
    dialog.setOnDismissListener(d->{if(dialog==created){dialog=null;release();}});
    dialog.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{release();listen();});
    listen();
  }

  private void listen() {
    if(offline){listenOffline();return;}
    try {
      status.setText("Say an English word…");
      recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
      SpeechRecognizer current=recognizer;
      recognizer.setRecognitionListener(new RecognitionListener(){
        public void onReadyForSpeech(Bundle params){if(recognizer==current)status.setText("Listening… Say an English word.");}
        public void onBeginningOfSpeech(){}
        public void onRmsChanged(float rms){}
        public void onBufferReceived(byte[] buffer){}
        public void onEndOfSpeech(){if(recognizer==current)status.setText("Finding your words…");}
        public void onError(int error){
          if(recognizer!=current)return;
          listening=false;
          String text;
          switch(error){
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:text="Allow microphone access in Android app settings, then try again.";break;
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:text="No word heard. Tap Try again and say an English word.";break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:text="Check your internet connection, then tap Try again.";break;
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:text="Enable English in your phone’s speech recognition settings, then try again.";break;
            default:text="Speech recognition could not finish. Tap Try again or check your voice input settings.";
          }
          status.setText(text);
        }
        public void onResults(Bundle values){
          if(recognizer!=current)return;
          ArrayList<String> words=values.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if(words==null||words.isEmpty()||words.get(0).trim().isEmpty()){
            onError(SpeechRecognizer.ERROR_NO_MATCH);return;
          }
          String text=words.get(0);stop();result.recognized(text);
        }
        public void onPartialResults(Bundle values){}
        public void onEvent(int event,Bundle values){}
      });
      listening=true;
      recognizer.startListening(request());
    }catch(RuntimeException error){
      release();status.setText("Could not start speech recognition. Check voice input and microphone access in Android settings.");
    }
  }

  private void listenOffline(){
    status.setText("Preparing offline voice input…");
    int current=++generation;
    offlineSession=offlineFactory.start(activity,new OfflineSpeech.Callback(){
      public void ready(){if(generation==current)status.setText("Listening… Say an English word.");}
      public void text(String text){if(generation==current){stop();result.recognized(text);}}
      public void error(String message){if(generation==current)status.setText(message);}
    });
  }

  void permissionResult(boolean granted) {
    if(granted)startService();
    else new AlertDialog.Builder(activity).setTitle("Allow microphone access")
        .setMessage("Voice search needs microphone access. Enable it in this app’s Android settings.")
        .setNegativeButton("Cancel",null).setPositiveButton("Settings",(d,w)->{
          activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              android.net.Uri.parse("package:"+activity.getPackageName())));
        }).show();
  }

  private void release(){
    generation++;
    if(offlineSession!=null){offlineSession.cancel();offlineSession=null;}
    if(recognizer!=null){
      SpeechRecognizer previous=recognizer;recognizer=null;
      if(listening)previous.cancel();
      previous.destroy();
    }
    listening=false;
  }
  void stop(){
    release();
    if(dialog!=null){AlertDialog previous=dialog;dialog=null;previous.dismiss();}
  }
}
