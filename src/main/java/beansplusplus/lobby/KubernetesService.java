package beansplusplus.lobby;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class KubernetesService {
  public static class KubernetesException extends Exception {
    public KubernetesException(Exception e) {
      super(e);
    }

    public void logStacktrace(Logger logger) {
      for (StackTraceElement element : getStackTrace()) {
        logger.severe(element.toString());
      }
    }
  }

  private static final V1Pod POD_TEMPLATE = createPodTemplate();

  private static final CoreV1Api API = new CoreV1Api();

  public static ApiClient setupClient() throws KubernetesException {
    try {
      ApiClient client = Config.defaultClient();
      Configuration.setDefaultApiClient(client);

      return client;
    } catch (IOException e) {
      throw new KubernetesException(e);
    }
  }

  private static V1Pod createPodTemplate() {
    try {
      return (V1Pod) Yaml.load(new File("pod.yaml"));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private GameServer server;

  public KubernetesService(GameServer server) {
    this.server = server;
  }

  public InetSocketAddress start() throws KubernetesException {
    try {
      // TODO meta data to pass game type? Or do we want different pod yamls per type?
      V1Pod pod = new V1PodBuilder()
          .withNewMetadataLike(POD_TEMPLATE.getMetadata())
          .withName("beans-mini-game-" + server.getId())
          .endMetadata()
          .withNewSpecLike(POD_TEMPLATE.getSpec())
          .endSpec()
          .build();

      V1Pod createdPod = API.createNamespacedPod("beans-mini-games", pod, null, null, null, null);

      return InetSocketAddress.createUnresolved(createdPod.getStatus().getPodIP(), 25565);
    } catch (ApiException e) {
      throw new KubernetesException(e);
    }
  }
}
