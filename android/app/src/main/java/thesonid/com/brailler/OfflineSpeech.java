package thesonid.com.brailler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

/** Bundled English recognition; no remote service or runtime model download is required. */
final class OfflineSpeech {
  interface Callback { void ready(); void text(String text); void error(String message); }
  interface Session { void cancel(); }
  interface Factory { Session start(Context context,Callback callback); }
  private static final ExecutorService IO=Executors.newSingleThreadExecutor();
  private static final Handler MAIN=new Handler(Looper.getMainLooper());

  static Session start(Context context,Callback callback){
    Run run=new Run(callback);
    Context app=context.getApplicationContext();
    IO.execute(()->{
      Model model=null;Recognizer decoder=null;
      try{
        File directory=prepareModel(app);
        if(run.cancelled)return;
        model=new Model(directory.getAbsolutePath());
        decoder=new Recognizer(model,16000f);
        Model loaded=model;Recognizer recognizer=decoder;
        MAIN.post(()->run.listen(loaded,recognizer));
      }catch(IOException|RuntimeException|LinkageError error){
        if(decoder!=null)decoder.close();if(model!=null)model.close();
        MAIN.post(()->{if(!run.cancelled)callback.error("Could not prepare offline voice input. Try again.");});
      }
    });
    return run;
  }

  static File prepareModel(Context app)throws IOException{
    File directory=new File(app.getNoBackupFilesDir(),"speech-en-us-0.15");
    File ready=new File(directory,".ready");
    if(!ready.isFile()){
      copy(app,"speech-model-en-us",directory);
      try(FileOutputStream marker=new FileOutputStream(ready)){marker.write(1);}
    }
    return directory;
  }

  private static void copy(Context app,String asset,File target)throws IOException{
    String[] children=app.getAssets().list(asset);
    if(children!=null&&children.length>0){
      if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Cannot create model folder");
      for(String child:children)copy(app,asset+"/"+child,new File(target,child));
    }else{
      try(InputStream input=app.getAssets().open(asset);OutputStream output=new FileOutputStream(target)){
        byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
      }
    }
  }

  private static final class Run implements Session,org.vosk.android.RecognitionListener {
    final Callback callback;
    volatile boolean cancelled;
    Model model;Recognizer decoder;SpeechService speech;
    Run(Callback callback){this.callback=callback;}
    void listen(Model loaded,Recognizer recognizer){
      model=loaded;decoder=recognizer;
      if(cancelled){cancel();return;}
      try{
        speech=new SpeechService(decoder,16000f);
        speech.startListening(this,10000);
        callback.ready();
      }catch(IOException|RuntimeException error){
        cancel();callback.error("Could not access the microphone. Check microphone access and try again.");
      }
    }
    private void result(String json,boolean last){
      if(cancelled)return;
      String text="";try{text=new JSONObject(json).optString("text","").trim();}catch(org.json.JSONException ignored){}
      if(!text.isEmpty()){callback.text(text);cancel();}
      else if(last)onTimeout();
    }
    public void onPartialResult(String json){}
    public void onResult(String json){result(json,false);}
    public void onFinalResult(String json){result(json,true);}
    public void onError(Exception error){if(!cancelled){callback.error("Could not record your voice. Check microphone access and try again.");cancel();}}
    public void onTimeout(){if(!cancelled){callback.error("No word heard. Tap Try again and say an English word.");cancel();}}
    public void cancel(){
      cancelled=true;
      SpeechService previous=speech;Recognizer previousDecoder=decoder;Model previousModel=model;
      speech=null;decoder=null;model=null;
      if(previous!=null||previousDecoder!=null||previousModel!=null)IO.execute(()->{
        if(previous!=null){previous.cancel();previous.shutdown();}
        if(previousDecoder!=null)previousDecoder.close();
        if(previousModel!=null)previousModel.close();
      });
    }
  }
}
