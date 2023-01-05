package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

public class GameServer {
  private final GameType type;
  private final String id;
  private int pingFails = 0;

  public GameServer(GameType type, String id) {
    this.type = type;
    this.id = id;
  }

  public GameType getType() {
    return type;
  }

  public String getId() {
    return id;
  }

  public ServerInfo getServerInfo() {
    return ProxyServer.getInstance().getServerInfo(id);
  }

  public int pingFail() {
    pingFails++;

    return pingFails;
  }

  public void pingSuccess() {
    pingFails = 0;
  }
}
