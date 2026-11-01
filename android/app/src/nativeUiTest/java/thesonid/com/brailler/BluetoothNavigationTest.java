package thesonid.com.brailler;

import static org.junit.Assert.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = BluetoothNavigationTest.TestSocket.class)
public class BluetoothNavigationTest {
    private BluetoothDevice device;

    @Before public void setUp() {
        Shadows.shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true);
        device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
    }

    @After public void tearDown() {
        BluetoothSession.disconnect();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void returningAndReopeningAfterSendKeepsConnectionAndIgnoresLateCallbacks()
            throws Exception {
        try (var controller = Robolectric.buildActivity(Host.class).setup()) {
            Host activity = controller.get();
            BluetoothChatFragment first = BluetoothChatFragment.forWord("hello");
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.sample_content_fragment, first).commitNow();
            Handler screen = ReflectionHelpers.getField(first, "handler");
            BluetoothChatService service = ReflectionHelpers.getField(first, "chatService");
            BluetoothSocket socket = socket();
            TestSocket io = Shadow.extract(socket);
            service.connected(socket, device, "test");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            android.widget.Button disconnect = first.requireView().findViewById(R.id.bluetooth_disconnect_button);
            assertTrue(disconnect.isEnabled());
            assertEquals(android.graphics.Color.WHITE, disconnect.getCurrentTextColor());
            capture(first.requireView(),"bluetooth-connected");
            first.requireView().findViewById(R.id.braille_load).performClick();
            assertTrue(io.written.await(2, TimeUnit.SECONDS));
            assertTrue(io.output.toString(StandardCharsets.UTF_8).contains("\"text\":\"hello\""));
            io.incoming.put(("{\"ok\":true,\"cmd\":\"load\",\"char\":\"h\","
                    + "\"index\":1,\"total\":5,\"dots\":\"110010\"}\n").getBytes(StandardCharsets.UTF_8));
            activity.getSupportFragmentManager().beginTransaction().remove(first).commitNow();
            // A worker that was already dispatching may still finish after onDestroyView.
            screen.handleMessage(screen.obtainMessage(Constants.MESSAGE_STATE_CHANGE,
                    BluetoothChatService.STATE_NONE, -1));
            screen.handleMessage(screen.obtainMessage(Constants.MESSAGE_READ, "late reply\n"));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(BluetoothChatService.STATE_CONNECTED, service.getState());
            assertFalse(io.closed);
            BluetoothChatFragment second = BluetoothChatFragment.forWord("world");
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.sample_content_fragment, second).commitNow();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame(service, ReflectionHelpers.getField(second, "chatService"));
            assertEquals(activity.getString(R.string.title_connected_to, "Braille board"),
                    ((TextView) second.requireView().findViewById(R.id.wifi_connection_status))
                            .getText().toString());
            second.requireView().findViewById(R.id.braille_load).performClick();
            assertTrue(io.twoWrites.await(2, TimeUnit.SECONDS));
            assertTrue(io.output.toString(StandardCharsets.UTF_8).contains("\"text\":\"world\""));
            second.requireView().findViewById(R.id.bluetooth_disconnect_button).performClick();
            Thread reader = ReflectionHelpers.getField(service, "mConnectedThread");
            assertNull(reader);
            assertTrue(io.closed);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(BluetoothChatService.STATE_NONE, service.getState());
            android.widget.Button disconnected = second.requireView().findViewById(R.id.bluetooth_disconnect_button);
            assertFalse(disconnected.isEnabled());
            assertEquals(ConstellateUi.forAccount(activity).ink, disconnected.getCurrentTextColor());
            capture(second.requireView(),"bluetooth-disconnected");
        }
    }

    @Test public void homeTracksBluetoothForBothExperiencesWithoutCreatingCloudBinding() {
        for(boolean user:new boolean[]{false,true}){
            BluetoothSession.disconnect();Shadows.shadowOf(Looper.getMainLooper()).idle();
            try(var controller=Robolectric.buildActivity(MainActivityBraille.class).setup()){
                MainActivityBraille activity=controller.get();SessionStore.clearSession(activity);
                ReflectionHelpers.setField(activity,"page","home");
                ReflectionHelpers.setField(activity,"userMode",user);
                ReflectionHelpers.setField(activity,"ui",new ConstellateUi(activity,false,user));
                android.widget.LinearLayout content=activity.findViewById(R.id.constellate_content);
                content.removeAllViews();ReflectionHelpers.callInstanceMethod(activity,"home");
                TextView status=ReflectionHelpers.getField(activity,"boardConnectionStatus");
                assertEquals("No board connected",status.getText().toString());
                BluetoothChatService service=ReflectionHelpers.getField(BluetoothSession.get(activity),"service");
                service.connected(socket(),device,"test");Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals("Connected to Braille board",status.getText().toString());
                assertNull(SessionStore.getBoardId(activity));
                // Returning from the Bluetooth activity also reads the current session immediately.
                controller.pause().stop();BluetoothSession.disconnect();Shadows.shadowOf(Looper.getMainLooper()).idle();
                controller.start().resume();assertEquals("No board connected",status.getText().toString());
                service.connected(socket(),device,"test");Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals("Connected to Braille board",status.getText().toString());
                BluetoothSession.disconnect();Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals("No board connected",status.getText().toString());
                SessionStore.saveSession(activity,"test-board");ReflectionHelpers.callInstanceMethod(activity,"updateBoardConnection");
                assertEquals("Board linked · Bluetooth not connected",status.getText().toString());
                SessionStore.clearSession(activity);
            }
        }
    }

    private static void capture(android.view.View root,String name)throws Exception{
        int width=390,height=600;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
        java.io.File out=new java.io.File("build/constellate-preview/"+name+".png");out.getParentFile().mkdirs();try(var stream=new java.io.FileOutputStream(out)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
    }

    @Test public void intentionalDisconnectDoesNotRestartListener() throws Exception {
        BluetoothChatService service = service();
        service.connected(socket(), device, "test");
        Thread reader = ReflectionHelpers.getField(service, "mConnectedThread");
        service.stop();
        reader.join(2000);
        assertFalse(reader.isAlive());
        assertEquals(BluetoothChatService.STATE_NONE, service.getState());
        assertNull(ReflectionHelpers.getField(service, "mSecureAcceptThread"));
        assertNull(ReflectionHelpers.getField(service, "mInsecureAcceptThread"));
    }

    @Test public void replacingConnectionIgnoresOldReaderFailure() throws Exception {
        BluetoothChatService service = service();
        try {
            service.connected(socket(), device, "test");
            Thread oldReader = ReflectionHelpers.getField(service, "mConnectedThread");
            BluetoothSocket replacement = socket();
            service.connected(replacement, device, "test");
            oldReader.join(2000);
            assertFalse(oldReader.isAlive());
            assertEquals(BluetoothChatService.STATE_CONNECTED, service.getState());
            assertFalse(((TestSocket) Shadow.extract(replacement)).closed);
        } finally { service.stop(); }
    }

    @Test public void cancelledConnectionFailureDoesNotRestartService() throws Exception {
        cancelConnecting(false);
    }

    @Test public void cancelledConnectionSuccessDoesNotReopenSocket() throws Exception {
        cancelConnecting(true);
    }

    private void cancelConnecting(boolean lateSuccess) throws Exception {
        BluetoothChatService service = service();
        try {
            service.connect(device, true);
            Thread connector = ReflectionHelpers.getField(service, "mConnectThread");
            BluetoothSocket socket = ReflectionHelpers.getField(connector, "mmSocket");
            TestSocket io = Shadow.extract(socket);
            io.completeAfterClose = lateSuccess;
            assertTrue(io.connectEntered.await(2, TimeUnit.SECONDS));
            service.stop();
            connector.join(2000);
            assertFalse(connector.isAlive());
            assertEquals(BluetoothChatService.STATE_NONE, service.getState());
            assertNull(ReflectionHelpers.getField(service, "mConnectedThread"));
            assertNull(ReflectionHelpers.getField(service, "mSecureAcceptThread"));
        } finally { service.stop(); }
    }

    @Test public void sendingUsesBackgroundThreadAndKeepsMessageOrder() throws Exception {
        BluetoothChatService service = service();
        BluetoothSocket socket = socket();
        TestSocket io = Shadow.extract(socket);
        try {
            service.connected(socket, device, "test");
            service.write("one\n".getBytes(StandardCharsets.UTF_8));
            service.write("two\n".getBytes(StandardCharsets.UTF_8));
            assertTrue(io.twoWrites.await(2, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), io.writer);
            assertEquals("one\ntwo\n", io.output.toString(StandardCharsets.UTF_8));
        } finally { service.stop(); }
    }

    @Test public void wordLibraryReusesLiveSessionAndRejectsDisconnectedSend() throws Exception {
        BluetoothSession.disconnect();
        assertFalse(BluetoothSession.sendWordIfConnected("cat"));
        BluetoothSession session = BluetoothSession.get(RuntimeEnvironment.getApplication());
        BluetoothChatService service = session.attach(new Handler(Looper.getMainLooper()));
        BluetoothSocket socket = socket();
        TestSocket io = Shadow.extract(socket);
        try {
            service.connected(socket, device, "test");
            assertTrue(BluetoothSession.sendWordIfConnected("cat"));
            assertTrue(io.written.await(2, TimeUnit.SECONDS));
            org.json.JSONObject message = new org.json.JSONObject(io.output.toString(StandardCharsets.UTF_8).trim());
            assertEquals("load", message.getString("cmd"));
            assertEquals("cat", message.getString("text"));
            assertFalse(BluetoothSession.sendWordIfConnected("invalid word"));
            BluetoothSession.disconnect();
            assertFalse(BluetoothSession.sendWordIfConnected("cat"));
        } finally { service.stop(); }
    }

    private BluetoothChatService service() {
        return new BluetoothChatService(org.robolectric.RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()));
    }

    private BluetoothSocket socket() { return Shadow.newInstanceOf(BluetoothSocket.class); }

    public static class Host extends AppCompatActivity {
        @Override protected void onCreate(Bundle state) {
            setTheme(R.style.ConstellateTheme);
            super.onCreate(state);
            FrameLayout frame = new FrameLayout(this);
            frame.setId(R.id.sample_content_fragment);
            setContentView(frame);
        }
    }

    @Implements(BluetoothSocket.class)
    public static class TestSocket {
        final LinkedBlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch written = new CountDownLatch(1), twoWrites = new CountDownLatch(2);
        final CountDownLatch connectEntered = new CountDownLatch(1), connectReleased = new CountDownLatch(1);
        volatile boolean closed;
        volatile boolean completeAfterClose;
        volatile Thread writer;

        @Implementation protected InputStream getInputStream() {
            return new InputStream() {
                @Override public int read() throws IOException { throw new IOException("Use bulk read"); }
                @Override public int read(byte[] buffer) throws IOException {
                    try {
                        byte[] value = incoming.take();
                        if (value.length == 0) throw new IOException("closed");
                        System.arraycopy(value, 0, buffer, 0, value.length);
                        return value.length;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            };
        }

        @Implementation protected OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) { output.write(value); }
                @Override public void write(byte[] value) throws IOException {
                    if (closed) throw new IOException("closed");
                    writer = Thread.currentThread();
                    output.write(value);
                    written.countDown();
                    twoWrites.countDown();
                }
            };
        }

        @Implementation protected void close() {
            closed = true;
            incoming.offer(new byte[0]);
            connectReleased.countDown();
        }

        @Implementation protected void connect() throws IOException {
            connectEntered.countDown();
            try {
                if (!connectReleased.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            if (closed && !completeAfterClose) throw new IOException("closed while connecting");
        }
    }
}
