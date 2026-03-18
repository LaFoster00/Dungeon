import dgir.vm.api.DapServerUtils.CollectingClient;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Mock-DAP integration test for visually inspecting debugger attach and pause overlay behavior. */
public class DungeonDebugAttachmentMockTest extends DungeonCompilerTestBase {

  @Test
  void mockDapClient_attach_stepAndResume_visualCheck() throws Exception {
    sendCodeWaitForDebugger(
        """
            public static void main() {
              IO.println("Debugger attach demo: started");
              Hero.rotate(Direction.LEFT);
              Hero.move();
              Hero.move();
              Hero.rotate(Direction.RIGHT);
              Hero.move();
              Hero.move();
              IO.println("Debugger attach demo: finished");
            }
            """);

    assertFalse(isRunning(), "Program should stay suspended before a debugger attaches");

    // Sleep briefly to allow time to switch to the game window and observe the paused state before
    // attaching.
    Thread.sleep(Duration.ofSeconds(2).toMillis());
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), 4711)) {
      CollectingClient client = new CollectingClient(10);
      Launcher<IDebugProtocolServer> launcher =
          DSPLauncher.createClientLauncher(
              client, socket.getInputStream(), socket.getOutputStream());
      IDebugProtocolServer dap = launcher.getRemoteProxy();
      Future<Void> listening = launcher.startListening();

      InitializeRequestArguments init = new InitializeRequestArguments();
      init.setClientID("blockly-visual-mock");
      dap.initialize(init).get(5, TimeUnit.SECONDS);
      client.awaitInitialized();

      dap.attach(Map.of()).get(5, TimeUnit.SECONDS);
      dap.configurationDone(new ConfigurationDoneArguments()).get(5, TimeUnit.SECONDS);

      var entryStop = client.awaitStopped();
      assertEquals(
          "entry", entryStop.getReason(), "Attach should stop at entry in waitForDebugger mode");

      for (int i = 0; i < 5; i++) {
        dap.next(new NextArguments()).get(5, TimeUnit.SECONDS);
        var step = client.awaitStopped();
        assertEquals("step", step.getReason(), "Step should emit a step stop");
      }

      System.out.println("Pausing program...");
      Thread.sleep(Duration.ofSeconds(2).toMillis());

      dap.continue_(new ContinueArguments()).get(5, TimeUnit.SECONDS);
      waitForCompletion(Duration.ofSeconds(20));
      assertFalse(isRunning(), "Program should complete after continue");

      socket.close();
      listening.cancel(true);
    }

    // Keep the test briefly alive so overlay hide-on-resume can be observed.
    Thread.sleep(Duration.ofSeconds(2).toMillis());
    assertTrue(true);
  }
}
