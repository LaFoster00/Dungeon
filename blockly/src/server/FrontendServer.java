package server;

import com.sun.net.httpserver.HttpServer;
import core.utils.logging.DungeonLogLevel;
import core.utils.logging.DungeonLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** This class is hosting the html files for the blockly dungeon. */
public class FrontendServer {

  private static final int PORT = 8081;
  private static final DungeonLogger LOGGER = DungeonLogger.getLogger(FrontendServer.class);
  private static HttpServer server;

  static {
    LOGGER.getBaseLogger().setLevel(DungeonLogLevel.INFO.toJulLevel());
  }

  /**
   * Starts the frontend server.
   *
   * @throws IOException if textures can not be loaded.
   */
  public static void run() throws IOException {
    if (server != null) {
      LOGGER.info("Server already running at http://localhost:" + PORT);
      return;
    }

    Path devDistDir = resolveDevDistDir();
    ensureFrontendAssetsAvailable(devDistDir);

    server = HttpServer.create(new InetSocketAddress(PORT), 0);
    LOGGER.debug(new File(".").getAbsolutePath());

    server.createContext(
        "/",
        exchange -> {
          String uriPath = exchange.getRequestURI().getPath();

          // Default auf index.html
          if (uriPath.equals("/")) {
            uriPath = "/index.html";
          }

          String resourcePath = "assets" + uriPath;

          InputStream is = loadAsset(resourcePath);

          // Fallback
          if (is == null) {
            resourcePath = "assets/index.html";
            is = loadAsset(resourcePath);
          }
          // SPA fallback
          if (is == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
          }
          byte[] bytes = is.readAllBytes();
          is.close();

          exchange.getResponseHeaders().add("Content-Type", guessMime(resourcePath));
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });

    server.setExecutor(null);
    server.start();
    LOGGER.info("Started successfully.");
    LOGGER.info("Connect via: http://localhost:" + PORT);
    LOGGER.info("Dev asset directory: " + devDistDir.toAbsolutePath());
  }

  private static String guessMime(String name) {
    if (name.endsWith(".html")) return "text/html";
    if (name.endsWith(".js")) return "application/javascript";
    if (name.endsWith(".css")) return "text/css";
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".svg")) return "image/svg+xml";
    if (name.endsWith(".json")) return "application/json";
    return "application/octet-stream";
  }

  private static InputStream loadAsset(String path) throws IOException {
    // load assets from jar asset path
    InputStream is = FrontendServer.class.getClassLoader().getResourceAsStream(path);
    if (is != null) return is;
    // fallback: loading assets from dev system
    Path devDistDir = resolveDevDistDir();
    Path relative = Paths.get(path.replaceFirst("^assets/?", ""));
    Path candidate = devDistDir.resolve(relative);
    if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
      return Files.newInputStream(candidate);
    }
    return null;
  }

  private static void ensureFrontendAssetsAvailable(Path devDistDir) {
    boolean hasPackagedAssets =
        FrontendServer.class.getClassLoader().getResource("assets/index.html") != null;
    if (hasPackagedAssets) {
      return;
    }

    if (devDistDir == null) {
      LOGGER.warn("Could not locate a frontend dist directory.");
      LOGGER.warn("Expected one of: blockly/frontend/dist or frontend/dist");
      LOGGER.warn("Server may start, but requests will return 404 until assets exist.");
      return;
    }

    Path indexHtml = devDistDir.resolve("index.html");
    if (Files.exists(indexHtml)) {
      return;
    }

    Path frontendDir = devDistDir.getParent();
    if (frontendDir == null || !Files.exists(frontendDir)) {
      LOGGER.warn("Frontend directory not found: " + devDistDir);
      LOGGER.warn("Server may start, but frontend assets are missing.");
      return;
    }

    LOGGER.info("Frontend assets missing. Trying to build automatically...");
    String npmCmd = isWindows() ? "npm.cmd" : "npm";

    try {
      int installExit = runCommand(frontendDir, List.of(npmCmd, "install"));
      if (installExit != 0) {
        LOGGER.error("Could not install frontend dependencies (exit " + installExit + ").");
        printManualFrontendHelp(frontendDir);
        return;
      }

      int buildExit = runCommand(frontendDir, List.of(npmCmd, "run", "build"));
      if (buildExit != 0) {
        LOGGER.error("Could not build frontend assets (exit " + buildExit + ").");
        printManualFrontendHelp(frontendDir);
        return;
      }

      if (Files.exists(indexHtml)) {
        LOGGER.info("Frontend assets built successfully.");
      } else {
        LOGGER.error("Frontend build finished but index.html is still missing.");
        printManualFrontendHelp(frontendDir);
      }
    } catch (Exception e) {
      LOGGER.error("Auto-build failed: " + e.getMessage());
      printManualFrontendHelp(frontendDir);
    }
  }

  private static Path resolveDevDistDir() {
    Path fromRoot = Paths.get("blockly", "frontend", "dist");
    if (Files.exists(fromRoot)) {
      return fromRoot;
    }
    Path fromModule = Paths.get("frontend", "dist");
    if (Files.exists(fromModule)) {
      return fromModule;
    }

    // Return a likely path so log output remains actionable even if it does not exist yet.
    return fromRoot;
  }

  private static int runCommand(Path workingDir, List<String> command)
      throws IOException, InterruptedException {
    LOGGER.info("Running: " + String.join(" ", command));
    Process process =
        new ProcessBuilder(command).directory(workingDir.toFile()).inheritIO().start();
    return process.waitFor();
  }

  private static void printManualFrontendHelp(Path frontendDir) {
    System.out.println(
        "Manual steps:"
            + "\n\tcd "
            + frontendDir.toAbsolutePath()
            + "\n\tnpm install"
            + "\n\t  npm run build");
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  /** Stops the frontend server. */
  public static void stopServer() {
    if (server != null) {
      server.stop(0);
      server = null;
      LOGGER.info("Stopped.");
    }
  }
}
