package beansplusplus.lobby;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import okhttp3.Call;

import java.io.*;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

// Remember: Don't forget to update the service account permissions if you're changing that this tries to access

public class KubernetesWorld {
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
  private static final String CHUNKY_PLUGIN_URL = "https://saggyresourcepack.blob.core.windows.net/www/Chunky-1.3.52.jar"; // can't use the spigot website :/
  private static final String K8S_NAMESPACE = "beans-mini-games";
  private static final V1Pod POD_TEMPLATE = createPodTemplate();
  private static final V1PersistentVolumeClaim PVC_TEMPLATE = createPersistentVolumeClaimTemplate();
  public static final ApiClient CLIENT = setupClient();
  private static final CoreV1Api CORE_API = new CoreV1Api();
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
      return (V1Pod) Yaml.load(new InputStreamReader(KubernetesWorld.class.getResourceAsStream("/pod.yaml")));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static V1PersistentVolumeClaim createPersistentVolumeClaimTemplate() {
    try {
      return (V1PersistentVolumeClaim) Yaml.load(new InputStreamReader(KubernetesWorld.class.getResourceAsStream("/pvc.yaml")));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private final String gameName;
  private final String id;

  // These get assigned when the kubernetes resource gets created
  private String gameJobName;
  private String preGenJobName;
  private String pvcName;

  private boolean preGenPaused = false;

  public KubernetesWorld(String id, boolean skipPreGen) throws ApiException {
    this.id = id;
    gameName = "beans-" + id;
    createPVC();

    if (!skipPreGen) {
      createPreGen();
    }
  }

  public InetSocketAddress start(String jarUrl) throws KubernetesException {
    try {
      // Create game pod
      gameJobName = gameName + "-game";
      createMinecraftPod(gameJobName, List.of(new String[]{CONFIG_PLUGIN_URL, jarUrl}), true);

      // Wait for pod to start then return IP
      Call call = CORE_API.listNamespacedPodCall(K8S_NAMESPACE, null, null, null, null, null, null, null, null, 300, true, null);
      Watch<V1Pod> watch = Watch.createWatch(CLIENT, call, new TypeToken<Watch.Response<V1Pod>>() {
      }.getType());
      for (Watch.Response<V1Pod> event : watch) {
        V1Pod p = event.object;
        if (
          p.getMetadata().getName().startsWith(gameJobName) // temporary. Replace with gameJobName
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
    if (gameJobName == null) {
      return false; // game hasn't started yet
    }
    try {
      V1Job job = BATCH_API.readNamespacedJob(gameJobName, K8S_NAMESPACE, null);
      return job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() == 1;
    } catch (ApiException e) {
      e.printStackTrace();
      // if there is an issue with connecting to the API server, it's safer to assume the game is still ongoing.
      return false;
    }
  }

  public boolean isPreGenFinished() {
    if (preGenJobName == null) {
      return true; // means it was skipped
    }
    try {
      V1Job job = BATCH_API.readNamespacedJob(preGenJobName, K8S_NAMESPACE, null);
      return job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() == 1;
    } catch (ApiException e) {
      e.printStackTrace();
      return false;
    }
  }

  private String createPVC() throws ApiException {
    pvcName = gameName + "-" + (System.currentTimeMillis() / 1000);
    V1PersistentVolumeClaim pvc = PVC_TEMPLATE.metadata(PVC_TEMPLATE.getMetadata().name(pvcName));
    pvc.getMetadata().putLabelsItem("beans-mini-game", "true");
    CORE_API.createNamespacedPersistentVolumeClaim(K8S_NAMESPACE, PVC_TEMPLATE, null, null, null, null);
    return pvc.getMetadata().getName();
  }

  private void createPreGen() throws ApiException {
    preGenJobName = gameName + "-pregen";
    createMinecraftPod(preGenJobName, List.of(new String[]{PREGEN_PLUGIN_URL, CHUNKY_PLUGIN_URL}), false);
  }

  private void createMinecraftPod(String podName, List<String> pluginURLs, boolean autoStop) throws ApiException {
    // Get copy of pod
    V1Pod pod = POD_TEMPLATE.metadata(POD_TEMPLATE.getMetadata().name(podName));

    // Set auto stop
    pod.getSpec().getContainers().get(0).addEnvItem(new V1EnvVar().name("ENABLE_AUTOSTOP").value(autoStop ? "TRUE" : "FALSE"));

    // Set Init command
    List<String> initCommand = new ArrayList<>(List.of(new String[]{"wget", "-P", "/plugins"}));
    initCommand.addAll(1, pluginURLs);
    pod.getSpec().getInitContainers().get(0).setCommand(initCommand);

    // Set volumes
    for (V1Volume volume : pod.getSpec().getVolumes()) {
      switch (volume.getName()) {
        case "world" -> volume.getPersistentVolumeClaim().claimName(pvcName);
      }
    }

    // Encapsulate in job
    V1Job job = new V1JobBuilder()
            .withNewMetadata()
              .withName(podName)
            .endMetadata()
            .withNewSpec()
              .withTemplate(new V1PodTemplateSpec().spec(pod.getSpec()))
              .withParallelism(1)
              .withCompletions(1)
              .withTtlSecondsAfterFinished(8000)
            .endSpec()
            .build();

    // Deploy job
    BATCH_API.createNamespacedJob(K8S_NAMESPACE, job, null, null, null, null);
  }

  public String getId() {
    return id;
  }

  public void pausePreGen() throws ApiException {
    //V1Job job = BATCH_API.readNamespacedJob(preGenJobName, K8S_NAMESPACE, null);
    //job.getSpec().suspend(true);
    //BATCH_API.patchNamespacedJob(preGenJobName, K8S_NAMESPACE, new V1Patch(Yaml.dump(job)), null, null, "example-field-manager", null, null);
    PatchUtils.patch(
            V1Job.class,
            () -> BATCH_API.patchNamespacedJobCall(
                    preGenJobName,
                    K8S_NAMESPACE,
                    new V1Patch("[{\"op\":\"replace\",\"path\":\"/spec/suspend\",\"value\":true}]"),
                    null,
                    null,
                    null, // field-manager is required for server-side apply
                    null,
                    true,
                    null),
            V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH,
            BATCH_API.getApiClient());
    preGenPaused = true;
  }

  public void resumePreGen() throws ApiException {
    V1Job job = BATCH_API.readNamespacedJob(preGenJobName, K8S_NAMESPACE, null);
    job.getSpec().suspend(false);
    //BATCH_API.patchNamespacedJob(preGenJobName, K8S_NAMESPACE, new V1Patch(Yaml.dump(job)), null, null, "example-field-manager", null, null);
    PatchUtils.patch(
            V1Job.class,
            () -> BATCH_API.patchNamespacedJobCall(
                    preGenJobName,
                    K8S_NAMESPACE,
                    new V1Patch("[{\"op\":\"replace\",\"path\":\"/spec/suspend\",\"value\":true}]"),
                    null,
                    null,
                    null, // field-manager is required for server-side apply
                    null,
                    true,
                    null),
            V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH,
            BATCH_API.getApiClient());
    preGenPaused = false;
  }

  public boolean isPreGenPaused() {
    return preGenPaused;
  }

  public static void removeOldGamePVC() {
    try {
      V1PersistentVolumeClaimList claims = CORE_API.listNamespacedPersistentVolumeClaim(K8S_NAMESPACE, null, null, null, null, "beans-mini-game=true", null, null, null, null, null);
      for (V1PersistentVolumeClaim claim : claims.getItems()) {
        if (OffsetDateTime.now().minus(Period.ofDays(7)).isAfter(claim.getMetadata().getCreationTimestamp())) { // TODO: This tracks from pre-gen time not game time
          System.out.println("PVC " + claim.getMetadata().getName() + " is over 7 days old. Deleting PVC");
          CORE_API.deleteNamespacedPersistentVolumeClaim(claim.getMetadata().getName(), K8S_NAMESPACE, null, null, null, null, null, null);
        }
      }
    } catch (ApiException e) {
      e.printStackTrace();
    }
  }
}
