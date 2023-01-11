package beansplusplus.lobby;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import okhttp3.Call;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

public class Kubernetes {
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

  private static final String CONFIG_PLUGIN_URL = "https://saggyresourcepack.blob.core.windows.net/www/GameConfigPlugin-1.0-SNAPSHOT.jar";

  private static final String K8S_NAMESPACE = "beans-mini-games";

  private static final V1Pod POD_TEMPLATE = createPodTemplate();
  private static final V1PersistentVolumeClaim PVC_TEMPLATE = createPersistentVolumeClaimTemplate();

  private static final V1ConfigMap CONFIGMAP_TEMPLATE = createConfigMapTemplate();

  public static final ApiClient CLIENT = setupClient();

  private static final CoreV1Api API = new CoreV1Api();

  private static Queue<String> worlds = new LinkedList<>();

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
      return (V1Pod) Yaml.load(new InputStreamReader(Kubernetes.class.getResourceAsStream("/pod.yaml")));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static V1ConfigMap createConfigMapTemplate() {
    try {
      return (V1ConfigMap) Yaml.load(new InputStreamReader(Kubernetes.class.getResourceAsStream("/config.yaml")));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static V1PersistentVolumeClaim createPersistentVolumeClaimTemplate() {
    try {
      return (V1PersistentVolumeClaim) Yaml.load(new InputStreamReader(Kubernetes.class.getResourceAsStream("/pvc.yaml")));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private final GameServer server;
  private final String podName;

  public Kubernetes(GameServer server) {
    this.server = server;
    podName = "beans-mini-game-" + server.getId();
  }

  public InetSocketAddress start() throws KubernetesException {
    try {
      String configMapName = podName;

      String jarUrl = server.getType().getJarURL();

      List<String> initCommand = List.of(new String[]{"wget", CONFIG_PLUGIN_URL, jarUrl, "-P", "/plugins"});

      V1Pod pod = POD_TEMPLATE.metadata(POD_TEMPLATE.getMetadata().name(podName));
      pod.getSpec().getInitContainers().get(0).setCommand(initCommand);
      for (V1Volume volume : pod.getSpec().getVolumes()) {
        switch (volume.getName()) {
          case "config":
            volume.getConfigMap().setName(configMapName);
            break;
          case "world":
            volume.getPersistentVolumeClaim().claimName(createPVC());
            break;
        }
      }

      V1ConfigMap config = CONFIGMAP_TEMPLATE.metadata(CONFIGMAP_TEMPLATE.getMetadata().name(configMapName));

      API.createNamespacedConfigMap(K8S_NAMESPACE, config, null, null, null, null);
      API.createNamespacedPod(K8S_NAMESPACE, pod, null, null, null, null);

      // Wait for pod to start then return IP
      Call call = API.listNamespacedPodCall(K8S_NAMESPACE, null, null, null, null, null, null, null, null, 300, true, null);
      Watch<V1Pod> watch = Watch.createWatch(CLIENT, call, new TypeToken<Watch.Response<V1Pod>>() {
      }.getType());
      for (Watch.Response<V1Pod> event : watch) {
        V1Pod p = event.object;
        if (
          p.getMetadata().getName().equals(podName)
          && p.getStatus().getContainerStatuses() != null
          && p.getStatus().getContainerStatuses().get(0).getReady()
        ) {
          int port = p.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();
          return InetSocketAddress.createUnresolved(p.getStatus().getPodIP(), port);
        }
      }

      throw new KubernetesException("Failed to start pod. Could not find in the list of running pods.");
    } catch (ApiException e) {
      throw new KubernetesException(e);
    }
  }

  public boolean isFinished() {
    try {
      V1Pod pod = API.readNamespacedPod(podName, K8S_NAMESPACE, null);
      return pod.getStatus().getPhase().equals("Succeeded");
    } catch (ApiException e) {
      e.printStackTrace();
      // if there is an issue with connecting to the API server, it's safer to assume the game is still ongoing.
      return false;
    }
  }

  private static String createPVC() {
    V1PersistentVolumeClaim pvc = PVC_TEMPLATE.metadata(PVC_TEMPLATE.getMetadata().name("beans-world-" + System.currentTimeMillis()));
    return pvc.getMetadata().getName();
  }
}
