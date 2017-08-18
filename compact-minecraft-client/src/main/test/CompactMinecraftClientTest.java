import com.bringholm.compactminecraftclient.v1_12_1.CompactMinecraftClient;

import java.io.DataInputStream;
import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
  This test is a bit on the complex side. You have to have a minecraft server jar named 'minecraft_server.jar' in the 'test-run' directory, agree to the EULA,
  set online-mode to false and port to 25569 in server.properties.

  If no minecraft_server.jar is found, the test is skipped.
 */
public class CompactMinecraftClientTest {
    @Test
    public void testClient() throws InterruptedException, IOException {
        // Skip this test if there isn't a minecraft jar
        if (!new File("minecraft_server.jar").exists()) {
            System.out.println("Skipped test: no minecraft_server.jar found!");
            return;
        }
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "minecraft_server.jar", "nogui");
        builder.directory(null);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        Object object = new Object();
        Thread.sleep(10000);
        CompactMinecraftClient.registerInboundPacket(TestingClient.LoginSuccessPacket.class, CompactMinecraftClient.LoginInPacketLoginSuccess.ID, CompactMinecraftClient.State.LOGIN);
        new TestingClient("TestingUser", "localhost", 25569, object).login();
        synchronized (object) {
            object.wait();
        }
        process.destroyForcibly();
        Assert.assertTrue("Did not receive login success packet", TestingClient.receivedLoginSuccessPacket);
    }

    public static class TestingClient extends CompactMinecraftClient {
        private final Object lock;
        public static boolean receivedLoginSuccessPacket;

        public TestingClient(String username, String host, int port, Object lock) {
            super(username, host, port);
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            super.close();
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        public static class LoginSuccessPacket extends LoginInPacketLoginSuccess {
            public LoginSuccessPacket(DataInputStream inputStream) throws IOException {
                super(inputStream);
            }

            @Override
            public void handle(CompactMinecraftClient client) throws IOException {
                client.handleDisconnect("Successfully tested!");
                receivedLoginSuccessPacket = true;
            }
        }
    }
}
