package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;

import java.util.logging.Logger;

public class GameServerException extends Exception {
  public GameServerException(Exception e) {
    super(e);
  }

  public GameServerException(String message) {
    super(message);
  }

  public void logError() {
    Logger logger = ProxyServer.getInstance().getLogger();
    logger.severe("Unexpected Game Server error occurred.");
    logger.severe("Message: " + getMessage());

    for (StackTraceElement element : getStackTrace()) {
      logger.severe(element.toString());
    }
  }
}