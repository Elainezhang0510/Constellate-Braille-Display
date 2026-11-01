package thesonid.com.brailler;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Exercises the bundled model and native libraries on the actual device, with prerecorded audio. */
@RunWith(AndroidJUnit4.class)
public class OfflineSpeechDeviceTest {
  @Test public void bundledEnglishModelRecognizesCatWithoutASystemSpeechService()throws Exception{
    Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
    try(Model model=new Model(OfflineSpeech.prepareModel(target).getAbsolutePath());
        Recognizer recognizer=new Recognizer(model,16000f);
        InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("voice-cat.pcm")){
      byte[] buffer=new byte[4096];int count;StringBuilder results=new StringBuilder();
      while((count=input.read(buffer))!=-1)if(recognizer.acceptWaveForm(buffer,count))results.append(recognizer.getResult());
      results.append(recognizer.getFinalResult());
      assertTrue("Recognition result: "+results,results.toString().contains("cat"));
    }
  }
}
