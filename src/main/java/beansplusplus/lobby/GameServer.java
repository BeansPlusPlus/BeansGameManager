package beansplusplus.lobby;

import io.kubernetes.client.openapi.ApiException;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class GameServer {
  private final GameType type;
  private final String id;
  private Kubernetes k8s;
  private InetSocketAddress address;

  public GameServer(GameType type, String id, Kubernetes k8s) {
    this.type = type;
    this.id = id;
    this.k8s = k8s;
  }

  public GameType getType() {
    return type;
  }

  public String getId() {
    return id;
  }

  public void start() throws Kubernetes.KubernetesException {
    address = k8s.start(type.getJarURL());
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
}
