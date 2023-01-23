package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.net.InetSocketAddress;

public class GameServer {
  private final GameType type;
  private final String id;
  private KubernetesWorld k8s;
  private InetSocketAddress address;

  public GameServer(GameType type, KubernetesWorld k8s, InetSocketAddress address) {
    this.type = type;
    id = k8s.getId();
    this.k8s = k8s;
    this.address = address;
  }

  public GameType getType() {
    return type;
  }

  public String getId() {
    return id;
  }

  public boolean isFinished() {
    return k8s.isGameFinished();
  }

  public InetSocketAddress getAddress() {
    return address;
  }

  public ServerInfo getServerInfo() {
    return ProxyServer.getInstance().getServerInfo(id);
  }

  public void connectPlayerToServer(ProxiedPlayer player) {
    player.connect(getServerInfo());
  }
}
