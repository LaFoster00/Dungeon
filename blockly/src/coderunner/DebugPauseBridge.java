package coderunner;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import contrib.systems.AttributeBarSystem;
import contrib.systems.DebugDrawSystem;
import contrib.systems.EventScheduler;
import contrib.hud.UIUtils;
import core.Game;
import core.System;
import core.systems.CameraSystem;
import core.systems.DrawSystem;
import core.systems.input.InputSystem;
import core.systems.input.JoystickSystem;
import dgir.vm.dap.DapServer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges DAP pause/resume events to the game loop by suspending gameplay systems on the GDX
 * thread.
 */
final class DebugPauseBridge {

  private static final String OVERLAY_TEXT = "DEBUGGER PAUSED";
  private static final String OVERLAY_ACTOR_NAME = "debugger-pause-overlay";

  private static final Set<Class<? extends System>> SYSTEMS_TO_KEEP_RUNNING =
      Set.of(
          DrawSystem.class,
          CameraSystem.class,
          InputSystem.class,
          JoystickSystem.class,
          DebugDrawSystem.class,
          AttributeBarSystem.class);

  private final Set<Class<? extends System>> pausedByDebugger = ConcurrentHashMap.newKeySet();
  private volatile boolean paused;
  private Table overlay;

  static void install(DapServer server) {
    DebugPauseBridge bridge = new DebugPauseBridge();
    server.setDebugPauseListener(bridge::onDebugPauseChanged);
  }

  private void onDebugPauseChanged(boolean shouldPause) {
    Runnable applyState =
        () -> {
          if (shouldPause) {
            pauseGameplaySystems();
          } else {
            resumeGameplaySystems();
          }
        };

    if (Gdx.app != null) {
      Gdx.app.postRunnable(applyState);
    } else {
      applyState.run();
    }
  }

  private void pauseGameplaySystems() {
    if (paused) {
      return;
    }
    paused = true;

    EventScheduler.setPausable(true);

    Game.systems()
        .forEach(
            (systemClass, system) -> {
              if (SYSTEMS_TO_KEEP_RUNNING.contains(systemClass)) {
                return;
              }
              if (!system.isRunning()) {
                return;
              }
              system.stop();
              pausedByDebugger.add(systemClass);
            });

    showOverlay();
  }

  private void resumeGameplaySystems() {
    if (!paused) {
      return;
    }
    paused = false;

    Game.systems()
        .forEach(
            (systemClass, system) -> {
              if (pausedByDebugger.contains(systemClass)) {
                system.run();
              }
            });

    pausedByDebugger.clear();
    EventScheduler.setPausable(false);
    hideOverlay();
  }

  private void showOverlay() {
    if (overlay != null) {
      return;
    }
    Game.stage()
        .ifPresent(
            stage -> {
              Table root = new Table();
              root.setName(OVERLAY_ACTOR_NAME);
              root.setFillParent(true);
              root.top();

              Label.LabelStyle style =
                  new Label.LabelStyle(UIUtils.defaultSkin().get(Label.LabelStyle.class));
              Label label = new Label(OVERLAY_TEXT, style);
              root.add(label).padTop(16f);

              stage.addActor(root);
              overlay = root;
            });
  }

  private void hideOverlay() {
    if (overlay == null) {
      return;
    }
    overlay.remove();
    overlay = null;
  }
}
