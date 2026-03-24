package server;

import client.Client;
import coderunner.BlocklyCodeRunner;
import com.badlogic.gdx.Gdx;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import contrib.systems.EventScheduler;
import contrib.utils.EntityUtils;
import core.Game;
import core.level.elements.ILevel;
import core.level.loader.DungeonLoader;
import core.utils.Point;
import core.utils.logging.DungeonLogger;
import level.BlocklyLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Controls communication between the Blockly frontend and the dungeon game. Provides HTTP endpoints
 * for executing Java code (/code), resetting execution (/reset), clearing global values (/clear),
 * querying levels (/levels, /level), retrieving language info (/language), and checking execution
 * status (/status).
 */
public class Server {
  private static final DungeonLogger LOGGER = DungeonLogger.getLogger(Server.class);

  // Singleton
  private static Server instance;

  /** Default port for the server. */
  public static final int DEFAULT_PORT = 8080;

  /** Constructor of the server. */
  private Server() {}

  /**
   * Singleton pattern. Get the instance of the server. If the server does not exist, create a new
   * server object. The servers run on the {@link #DEFAULT_PORT}.
   *
   * @return Returns the server object.
   */
  public static Server instance() {
    if (instance == null) {
      instance = new Server();
    }
    return instance;
  }

  /**
   * Start the server and listen on the start, reset, clear interfaces. This server will be started
   * by the client.
   *
   * @return Returns the server object.
   * @throws IOException Throws an IOException if the server could not be started.
   */
  public HttpServer start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", DEFAULT_PORT), 0);
    HttpContext resetContext = server.createContext("/reset");
    resetContext.setHandler(this::handleResetRequest);
    HttpContext levelsContext = server.createContext("/levels");
    levelsContext.setHandler(this::handleLevelsRequest);
    HttpContext levelContext = server.createContext("/level");
    levelContext.setHandler(this::handleLevelRequest);
    HttpContext codeContext = server.createContext("/code");
    codeContext.setHandler(this::handleCodeRequest);
    HttpContext languageContext = server.createContext("/language");
    languageContext.setHandler(this::handleLanguageRequest);
    HttpContext statusContext = server.createContext("/status");
    statusContext.setHandler(this::handleStatusRequest);
    server.start();
    return server;
  }

  // ---------------------------------------------------------
  // CORS Headers and Handling
  // ---------------------------------------------------------

  /**
   * Applies permissive CORS headers so browser-based clients can call the API without being blocked
   * by cross-origin restrictions. Also declares allowed methods/headers and caches preflight for a
   * day.
   */
  private void addCorsHeaders(HttpExchange exchange) {
    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    exchange
        .getResponseHeaders()
        .add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
  }

  /**
   * Responds to CORS preflight requests (OPTIONS) early. Returns true when the request was handled
   * so callers can exit without further processing.
   */
  private boolean handleOptions(HttpExchange exchange) throws IOException {
    if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
      return false;
    }
    sendResponse(StatusCode.NO_CONTENT, null, exchange);
    exchange.close();
    return true;
  }

  /**
   * Ensures the incoming HTTP method matches the expected one, replying with 405 when it does not
   * and short-circuiting OPTIONS preflight. Returns false when the caller should stop processing.
   */
  private boolean ensureMethod(HttpExchange exchange, String expectedMethod) throws IOException {
    if (handleOptions(exchange)) return false;
    if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
      sendResponse(StatusCode.METHOD_NOT_ALLOWED, "Method Not Allowed", exchange);
      return false;
    }
    return true;
  }

  // ---------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------

  private void sendResponse(
      @NotNull StatusCode code, @Nullable String response, @NotNull HttpExchange exchange)
      throws IOException {
    addCorsHeaders(exchange);
    if (response == null) {
      exchange.sendResponseHeaders(code.value, -1);
    } else {
      exchange.sendResponseHeaders(code.value, response.getBytes(StandardCharsets.UTF_8).length);
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes(StandardCharsets.UTF_8));
      os.close();
    }
  }

  /**
   * Wait for the delta time of the current frame.
   *
   * <p>Used to wait for the game loop to finish the current frame before executing the next action.
   */
  public static void waitDelta() {
    long timeout = (long) (Gdx.graphics.getDeltaTime() * 1000);
    try {
      TimeUnit.MILLISECONDS.sleep(timeout - 1);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parses the raw query string from the request URI into a map of parameter names to their
   * (possibly multiple) values. Decodes URL-encoded characters so callers can safely read user
   * input.
   */
  private Map<String, List<String>> parseQueryParams(HttpExchange exchange) {
    String rawQuery = exchange.getRequestURI().getRawQuery();
    if (rawQuery == null || rawQuery.isEmpty()) return Collections.emptyMap();

    Map<String, List<String>> params = new HashMap<>();
    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) continue;
      String[] kv = pair.split("=", 2);
      String key = urlDecode(kv[0]);
      String value = kv.length > 1 ? urlDecode(kv[1]) : "";
      params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }
    return params;
  }

  /**
   * Returns the first value for a named query parameter or null when the parameter is absent. Helps
   * handlers avoid repeated null/empty checks.
   */
  private String firstParam(Map<String, List<String>> params, String name) {
    List<String> values = params.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  /** URL-decodes a single value using UTF-8. Keeps query parsing readable in one place. */
  private String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  /**
   * Collects the set of Blockly blocks that are disabled for the given level so the frontend can
   * hide or gray them out.
   */
  private @Unmodifiable Set<String> blockedBlocksForLevel(@Nullable ILevel level) {
    if (level instanceof BlocklyLevel blocklyLevel) {
      return Collections.unmodifiableSet(blocklyLevel.blockedBlocklyElements());
    }
    return Collections.emptySet();
  }

  private String stopExecution() {
    if (BlocklyCodeRunner.instance().isRunning()) {
      BlocklyCodeRunner.instance().stopExecution();
      return "Code execution stopped";
    } else {
      return "No code execution running";
    }
  }

  private void sendHeroPosition(HttpExchange exchange) throws IOException {
    Point playerPos = EntityUtils.getPlayerPosition();
    if (playerPos == null) {
      playerPos = new Point(0, 0);
    }
    sendResponse(StatusCode.OK, playerPos.toString(), exchange);
  }

  // ---------------------------------------------------------
  // Endpoint Handlers
  // ---------------------------------------------------------

  /**
   * Handles the reset request. This function will set the boolean interruptExecution. The execution
   * will be stopped.
   *
   * @param exchange Exchange object. The function will send a success response to the blockly
   *     frontend
   * @throws IOException If an error occurs while sending the response
   */
  private void handleResetRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received reset request");
    if (!ensureMethod(exchange, "POST")) return;
    Gdx.app.postRunnable(Client::restart);
    sendHeroPosition(exchange);
  }

  /**
   * Handles the levels request. This function will send all available levels to the blockly
   * frontend.
   *
   * @param exchange Exchange object. The function will send a success response to the blockly
   *     frontend
   */
  private void handleLevelsRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received levels request");
    if (!ensureMethod(exchange, "GET")) return;
    String response = String.join("\n", DungeonLoader.levelOrder());
    sendResponse(StatusCode.OK, response, exchange);
  }

  /**
   * Handles the level request. This function will send the current level with its blocked blocks or
   * if give will first change the level to the given one and then send the current level with its
   * blocked blocks.
   *
   * @param exchange Exchange object. The function will send a success response to the blockly
   *     frontend
   */
  private void handleLevelRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received level request" + exchange.getRequestURI().getRawQuery());
    if (!ensureMethod(exchange, "GET")) return;
    Map<String, List<String>> queryParams = parseQueryParams(exchange);
    StringBuilder response = new StringBuilder();

    String levelName = firstParam(queryParams, "levelName");

    if (levelName != null && !levelName.equals(DungeonLoader.currentLevel())) {
      // if given and the level is not the current one, load it
      // use the eventschedular to load the level in the game thread
      EventScheduler.scheduleAction(() -> DungeonLoader.loadLevel(levelName), 0);
      for (int i = 0; i < 5; i++) {
        waitDelta(); // waiting for all systems to update once
      }
    }
    response.append(DungeonLoader.currentLevel()).append(" ");
    response.append(String.join(" ", blockedBlocksForLevel(Game.currentLevel().orElse(null))));
    sendResponse(StatusCode.OK, response.toString(), exchange);
  }

  /**
   * Handles the code request. This function will execute the given java code. The code must be
   * formatted as a string and will be executed in the dungeon.
   *
   * @param exchange Exchange object
   * @throws IOException If an error occurs while sending the response
   */
  private void handleCodeRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received code request: " + exchange.getRequestURI().getRawQuery());
    if (!ensureMethod(exchange, "POST")) return;
    Map<String, List<String>> queryParams = parseQueryParams(exchange);
    // Handle stop request
    {
      boolean isStopRequest = queryParams.containsKey("stop");
      if (isStopRequest) {
        LOGGER.info("Received stop request");
        String response = stopExecution();
        sendResponse(StatusCode.OK, response, exchange);
        return;
      }
    }

    final boolean stoppedExecution;
    // Handle normal code execution request
    if (BlocklyCodeRunner.instance().isRunning()) {
      Client.restart();
      waitDelta();
      stoppedExecution = true;
    } else {
      stoppedExecution = false;
    }

    boolean isCompleteProgram = queryParams.containsKey("complete");
    boolean waitForDebugger = queryParams.containsKey("waitForDebugger");
    String sourceFileNameParam = firstParam(queryParams, "sourceFileName");

    int sleepAfterEachLine = -1;
    String sleepParam = firstParam(queryParams, "sleep");
    if (sleepParam != null) {
      try {
        sleepAfterEachLine = Math.max(Integer.parseInt(sleepParam), 0);
      } catch (NumberFormatException e) {
        LOGGER.warn("Invalid sleep parameter: " + sleepParam);
      }
    }

    InputStream inStream = exchange.getRequestBody();
    String code = new String(inStream.readAllBytes(), StandardCharsets.UTF_8);

    final int finalSleepAfterEachLine = sleepAfterEachLine;
    Gdx.app.postRunnable(
        () -> {
          LOGGER.info("Handling code request.");
          // Start code execution
          try {
            if (finalSleepAfterEachLine >= 0) {
              BlocklyCodeRunner.instance()
                  .compileAndRunCode(
                      code,
                      finalSleepAfterEachLine,
                      isCompleteProgram,
                      waitForDebugger,
                      sourceFileNameParam);
            } else {
              BlocklyCodeRunner.instance()
                  .compileAndRunCode(code, isCompleteProgram, waitForDebugger, sourceFileNameParam);
            }
            sendResponse(
                StatusCode.OK,
                "OK - Code execution started"
                    + (stoppedExecution ? "Stopped running code previously running code." : ""),
                exchange);
          } catch (Exception e) {
            LOGGER.error("Exception executing code: " + e);
            stopExecution();
            try {
              sendResponse(StatusCode.BAD_REQUEST, e.getMessage(), exchange);
            } catch (IOException ex) {
              LOGGER.error("Failed to send error response: " + ex);
            }
          }
        });
  }

  /**
   * Handles the language request. This function will return the current language of the dungeon.
   *
   * @param exchange Exchange object. The function will send a success response to the blockly
   *     frontend
   * @throws IOException If an error occurs while sending the response
   */
  private void handleLanguageRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received language request" + exchange.getRequestURI().getRawQuery());
    if (!ensureMethod(exchange, "GET")) return;
    addCorsHeaders(exchange);

    Map<String, List<String>> queryParams = parseQueryParams(exchange);
    String objectName = firstParam(queryParams, "object");
    if (objectName == null) objectName = "/server";
    sendResponse(StatusCode.OK, LanguageServer.GenerateCompletionItems(objectName), exchange);
  }

  /**
   * Handles the status request. This function will return the current status of the Blockly -
   * UserScript Code.
   *
   * @param exchange Exchange object
   * @throws IOException If an error occurs while sending the response
   */
  private void handleStatusRequest(HttpExchange exchange) throws IOException {
    LOGGER.info("Received status request");
    if (!ensureMethod(exchange, "GET")) return;
    String response;
    if (BlocklyCodeRunner.instance().isRunning()) {
      response = "running";
    } else {
      response = "completed";
    }
    sendResponse(StatusCode.OK, response, exchange);
  }
}
