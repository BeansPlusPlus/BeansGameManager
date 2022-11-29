package beansplusplus.lobby;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import okhttp3.Call;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Logger;

public class KubernetesService {
  public static class KubernetesException extends Exception {
    public KubernetesException(Exception e) {
      super(e);
    }

    public KubernetesException(String message) {
      super(message);
    }

    public void logError(Logger logger) {
      logger.severe("Message: " + getMessage());

      for (StackTraceElement element : getStackTrace()) {
        logger.severe(element.toString());
      }
    }
  }

  private static final String K8S_NAMESPACE = "beans-mini-games";

  private static final V1Pod POD_TEMPLATE = createPodTemplate();

  public static final ApiClient CLIENT = setupClient();

  private static final CoreV1Api API = new CoreV1Api();

  private static ApiClient setupClient() {
    try {
      ApiClient client = Config.defaultClient();
      Configuration.setDefaultApiClient(client);

      return client;
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static V1Pod createPodTemplate() {
    try {
      return (V1Pod) Yaml.load(new InputStreamReader(KubernetesService.class.getResourceAsStream("/pod.yaml")));
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
      String podName = "beans-mini-game-" + server.getId();

      String jarUrl = server.getType().getJarURL();

      V1Pod pod = new V1PodBuilder()
          .withNewMetadataLike(POD_TEMPLATE.getMetadata())
          .withName("beans-mini-game-" + server.getId())
          .endMetadata()
          .withNewSpecLike(POD_TEMPLATE.getSpec())
          .endSpec()
          .withKind(POD_TEMPLATE.getKind())
          .withApiVersion(POD_TEMPLATE.getApiVersion())
          .build();

      List<String> initCommand = List.of(new String[]{"wget", jarUrl, "-P", "/data/plugins"});
      pod.getSpec().getInitContainers().get(0).setCommand(initCommand);

      System.out.println(Yaml.dump(pod));

      API.createNamespacedPod(K8S_NAMESPACE, pod, null, null, null, null);

      // Wait for pod to start then return IP
      Call call = API.listNamespacedPodCall(K8S_NAMESPACE, null, null, null, null, null, null, null, null, 300, true, null);
      Watch<V1Pod> watch = Watch.createWatch(CLIENT, call, new TypeToken<Watch.Response<V1Pod>>() {
      }.getType());
      for (Watch.Response<V1Pod> event : watch) {
        V1Pod p = event.object;
        if (p.getMetadata().getName().equals(podName) && p.getStatus().getContainerStatuses().get(0).getReady()) {
          int port = p.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();
          return InetSocketAddress.createUnresolved(p.getStatus().getPodIP(), port);
        }
      }

      throw new KubernetesException("Failed to start pod. Could not find in the list of running pods.");
    } catch (ApiException e) {
      throw new KubernetesException(e);
    }
  }
}
