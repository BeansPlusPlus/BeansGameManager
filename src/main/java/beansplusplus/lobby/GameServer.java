package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class GameServer {
  private final GameType type;
  private final String id;
  private Kubernetes k8s;
  private InetSocketAddress address;

  public GameServer(GameType type, String id) {
    this.type = type;
    this.id = id;
    k8s = new Kubernetes(this);
  }

  public GameType getType() {
    return type;
  }

  public String getId() {
    return id;
  }

  public void start() throws Kubernetes.KubernetesException {
    address = k8s.start();
  }

  public boolean isFinished() {
    return k8s.isFinished();
  }

  public InetSocketAddress getAddress() {
    return address;
  }

  public ServerInfo getServerInfo() {
    return ProxyServer.getInstance().getServerInfo(id);
  }
}
