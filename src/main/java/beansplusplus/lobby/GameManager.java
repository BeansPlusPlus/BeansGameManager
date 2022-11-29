package beansplusplus.lobby;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import okhttp3.Call;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

public class GameManager {
  private static final GameManager GAME_MANAGER = new GameManager();

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private final Random random = new Random();

  private final Map<String, GameServer> gameServers = new HashMap<>();

  private static V1Pod podTemplate = null;

  private static final String K8S_NAMESPACE = "beans-mini-games";

  /**
   * Create a new server by game type
   *
   * @param type
   * @return
   */
  public GameServer createServer(GameType type) {
    GameServer gameServer = new GameServer(type, generateId());

    InetSocketAddress address = startServer(gameServer.getId());

    registerServer(gameServer, address);

    return gameServer;
  }

  /**
   * Get a server by id
   *
   * @param id
   * @return
   */
  public GameServer getServer(String id) {
    if (!gameServers.containsKey(id)) {
      return null;
    }

    return gameServers.get(id);
  }

  /**
   * Get all game server ids
   *
   * @return
   */
  public Collection<String> getAvailableGameIds() {
    return gameServers.keySet();
  }

  /**
   * Start up a new server
   *
   * @return
   */
  private InetSocketAddress startServer(String gameId) {
    try {
      // load pod template
      if (podTemplate == null) {
        podTemplate = (V1Pod)Yaml.load(new File("pod.yaml"));
      }

      // setup kubernetes client
      ApiClient client = Config.defaultClient();
      Configuration.setDefaultApiClient(client);
      CoreV1Api api = new CoreV1Api();


      // deploy mini game pod
      String podName = "beans-mini-game-" + gameId;
      podTemplate.setMetadata(new V1ObjectMetaBuilder().withName(podName).build());
      api.createNamespacedPod(K8S_NAMESPACE, podTemplate, null, null, null, null);

      // Wait for pod to start then return IP
      Call call = api.listNamespacedPodCall(K8S_NAMESPACE, null, null, null, null, null, null, null, null, 300, true, null);
      Watch<V1Pod> watch = Watch.createWatch(client, call, new TypeToken<Watch.Response<V1Pod>>(){}.getType());
      for (Watch.Response<V1Pod> event : watch) {
        V1Pod pod = event.object;
        if (pod.getMetadata().getName().equals(podName) && pod.getStatus().getPhase().equals("Running")) {
          return InetSocketAddress.createUnresolved(pod.getStatus().getPodIP(), 25565);
        }
      }
    } catch (IOException | ApiException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void registerServer(GameServer gameServer, InetSocketAddress address) {
    ServerInfo info = ProxyServer.getInstance().constructServerInfo(gameServer.getId(), address, "BeansPlusPlus Server", false);

    ProxyServer.getInstance().getServers().put(gameServer.getId(), info); // register to proxy

    gameServers.put(gameServer.getId(), gameServer); // add to game manager
  }

  /**
   * Generate unique 5 digit number
   *
   * @return
   */
  private String generateId() {
    String id = null;

    while (id == null || gameServers.containsKey(id)) {
      id = "" + (random.nextInt(90000) + 10000);
    }

    return id;
  }
}
