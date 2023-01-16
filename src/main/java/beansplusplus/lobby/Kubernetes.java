package beansplusplus.lobby;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import okhttp3.Call;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Logger;

// Remember: Don't forget to update the service account permissions if you're changing that this tries to access

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
  private static final String PREGEN_PLUGIN_URL = "https://saggyresourcepack.blob.core.windows.net/www/PreGen-1.0.jar";
  private static final String K8S_NAMESPACE = "beans-mini-games";
  private static final V1Pod POD_TEMPLATE = createPodTemplate();
  private static final V1PersistentVolumeClaim PVC_TEMPLATE = createPersistentVolumeClaimTemplate();
  private static final V1ConfigMap CONFIGMAP_TEMPLATE = createConfigMapTemplate();
  public static final ApiClient CLIENT = setupClient();
  private static final CoreV1Api API = new CoreV1Api();
  private static final BatchV1Api BATCH_API = new BatchV1Api();

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

  private final String gameName;

  // These get assigned when the kubernetes resource gets created
  private String gamePodName;
  private String preGenPodName;
  private String configMapName;
  private String pvcName;


  public Kubernetes(String id, boolean skipPreGen) throws ApiException {
    gameName = "beans-" + id;
    createConfigMap();
    createPVC();
    if (!skipPreGen) {
      createPreGen();
    }
  }

  public InetSocketAddress start(String jarUrl) throws KubernetesException {
    try {
      // Create game pod
      gamePodName = gameName + "game";
      List<String> initCommand = List.of(new String[]{"wget", CONFIG_PLUGIN_URL, jarUrl, "-P", "/plugins"});
      createMinecraftPod(gamePodName, initCommand);

      // Wait for pod to start then return IP
      Call call = API.listNamespacedPodCall(K8S_NAMESPACE, null, null, null, null, null, null, null, null, 300, true, null);
      Watch<V1Pod> watch = Watch.createWatch(CLIENT, call, new TypeToken<Watch.Response<V1Pod>>() {
      }.getType());
      for (Watch.Response<V1Pod> event : watch) {
        V1Pod p = event.object;
        if (
          p.getMetadata().getName().equals(gamePodName)
          && p.getStatus().getContainerStatuses() != null
          && p.getStatus().getContainerStatuses().get(0).getReady()
        ) {
          int port = p.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();
          return InetSocketAddress.createUnresolved(p.getStatus().getPodIP(), port);
        }
      }

      throw new KubernetesException("Failed to start pod. Could not find in the list of running pods.");
    } catch (ApiException e) {
      e.printStackTrace();
      throw new KubernetesException(e);
    }
  }

  public boolean isGameFinished() {
    if (gamePodName == null) {
      return false;
    }
    try {
      V1Pod pod = API.readNamespacedPod(gamePodName, K8S_NAMESPACE, null);
      return pod.getStatus().getPhase().equals("Succeeded");
    } catch (ApiException e) {
      e.printStackTrace();
      // if there is an issue with connecting to the API server, it's safer to assume the game is still ongoing.
      return false;
    }
  }

  public boolean isPreGenFinished() {
    if (preGenPodName == null) {
      return true; // means it was skipped
    }
    try {
      V1Pod pod = API.readNamespacedPod(preGenPodName, K8S_NAMESPACE, null);
      return pod.getStatus().getPhase().equals("Succeeded");
    } catch (ApiException e) {
      e.printStackTrace();
      return false;
    }
  }

  private String createPVC() throws ApiException {
    pvcName = gameName + (System.currentTimeMillis() / 1000);
    V1PersistentVolumeClaim pvc = PVC_TEMPLATE.metadata(PVC_TEMPLATE.getMetadata().name(pvcName));
    API.createNamespacedPersistentVolumeClaim(K8S_NAMESPACE, PVC_TEMPLATE, null, null, null, null);
    return pvc.getMetadata().getName();
  }

  private void createPreGen() throws ApiException {
    preGenPodName = gameName + "pregen";
    List<String> initCommand = List.of(new String[]{"wget", PREGEN_PLUGIN_URL, "-P", "/plugins"});
    createMinecraftPod(preGenPodName, initCommand);
  }

  private void createConfigMap() throws ApiException {
    configMapName = gameName;
    V1ConfigMap config = CONFIGMAP_TEMPLATE.metadata(CONFIGMAP_TEMPLATE.getMetadata().name(configMapName));
    API.createNamespacedConfigMap(K8S_NAMESPACE, config, null, null, null, null);
  }

  private void createMinecraftPod(String podName, List<String> initCommand) throws ApiException {
    V1Pod pod = POD_TEMPLATE.metadata(POD_TEMPLATE.getMetadata().name(podName));
    pod.getSpec().getInitContainers().get(0).setCommand(initCommand);
    for (V1Volume volume : pod.getSpec().getVolumes()) {
      switch (volume.getName()) {
        case "config" -> volume.getConfigMap().setName(configMapName);
        case "world" -> volume.getPersistentVolumeClaim().claimName(pvcName);
      }
    }
    V1Job job = new V1JobBuilder()
            .editMetadata()
            .withName(podName)
            .endMetadata()
            .editSpec()
            .withTemplate(new V1PodTemplateSpec().spec(pod.getSpec()))
            .withParallelism(1)
            .withCompletions(1)
            .withTtlSecondsAfterFinished(8000)
            .endSpec()
            .build();
      BATCH_API.createNamespacedJob(K8S_NAMESPACE, job, null, null, null, null);
  }
}
