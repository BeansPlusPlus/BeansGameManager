package beansplusplus.lobby;

/*
 * PVC Life Cycle:
 * When created: stage=pre-gen
 * When pre-gen done: stage=ready
 * When game starts: stage=game
 * When game done: stage=finished, game-finish-time=[time of game finishing]
 */

/*
 * Pre-gen jobs will be excited by the kube-scheduler when a game starts (unless you have a very powerful computer).
 * This is intended behavior.
 */

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;

import javax.print.attribute.standard.PrinterMakeAndModel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class KubernetesManager {

    private static final String STORAGE_CLASS_NAME = withEnv("K8S_STORAGE_CLASS_NAME", "local-path");
    private static final String NAMESPACE = withEnv("K8S_NAMESPACE", "beans-mini-games");
    private static final V1Job PRE_GEN_JOB = getJobTemplate("/pre-gen-job.yaml");
    private static final V1Job GAME_JOB = getJobTemplate("/game-job.yaml");
    private static final String GAME_PLUGIN_URL = "https://saggyresourcepack.blob.core.windows.net/www/BeansGamePlugin-1.0-SNAPSHOT.jar";
    private static final int PRE_GEN_NUM = 10;
    private static final int PRE_GEN_MAX_SIMULTANEOUS_JOBS = 1;
    private static final Random random = new Random();

    private static String withEnv(String key, String default_) {
        Map<String, String> env = System.getenv();
        if (env.containsKey(key)) {
            return env.get(key);
        }
        return default_;
    }

    private static V1Job getJobTemplate(String filename) {
        try {
            return (V1Job) Yaml.load(new InputStreamReader(KubernetesManager.class.getResourceAsStream(filename)));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    // Keep all state stored in Kubernetes
    private final CoreV1Api coreV1Api = new CoreV1Api();
    private final BatchV1Api batchV1Api = new BatchV1Api();

    static {
        try {
            Configuration.setDefaultApiClient(Config.defaultClient());
        } catch (IOException e) {
            throw new Error(e);
        }
    }


    /**
     * This function is intended to be run on a schedule. E.g. Ran every 5 seconds
     * @throws GameServerException
     */
    public void tick() throws GameServerException {
        try {
            // update PVC stages and clean up old jobs
            // for all pre-gen jobs
            V1JobList preGenJobs = batchV1Api.listNamespacedJob(NAMESPACE, null, null, null, null, "purpose=beans-pre-gen", null, null, null, null, null);
            for (V1Job preGenJob : preGenJobs.getItems()) {
                // if job finished
                if (preGenJob.getStatus().getSucceeded() != null && preGenJob.getStatus().getSucceeded() == 1) {
                    // Update claim stage and delete job
                    String claimName = preGenJob.getSpec().getTemplate().getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
                    setClaimStage(claimName, "ready");
                    batchV1Api.deleteNamespacedJob(preGenJob.getMetadata().getName(), NAMESPACE, null, null, null, null, null, null);
                }
            }

            // for all game jobs
            V1JobList gameJobs = batchV1Api.listNamespacedJob(NAMESPACE, null, null, null, null, "purpose=beans-game", null, null, null, null, null);
            for (V1Job gameJob : gameJobs.getItems()) {
                // if job finished
                if (gameJob.getStatus().getSucceeded() != null && gameJob.getStatus().getSucceeded() == 1) {
                    // Update claim stage and delete job
                    String claimName = gameJob.getSpec().getTemplate().getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
                    setClaimStage(claimName, "finished");
                    setClaimGameFinishTimeToNow(claimName);
                    batchV1Api.deleteNamespacedJob(gameJob.getMetadata().getName(), NAMESPACE, null, null, null, null, null, null);
                }
            }

            // create new pre generated world if needed
            int current = getGeneratingNum();
            int done = getCompletedGenerationNum();
            int needed = PRE_GEN_NUM - done - current;
            int max = PRE_GEN_MAX_SIMULTANEOUS_JOBS - current;
            int toCreate = Math.min(max, needed);

            for (int i = 0; i < toCreate; i++) {
                createPreGenWorld();
            }
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private String getNewGameId() throws GameServerException {
        Set<String> gameIds = getGames().keySet();
        int offset = random.nextInt(100);
        for (int i = 0; i < 100; i++) {
            int id = (i + offset) % 100;
            String strId = String.format("%02d", id);
            if (!gameIds.contains(strId)) {
                return strId;
            }
        }
        throw new GameServerException("Out of games IDs. The number of games exceeds 100");
    }

    /**
     * Gets the number of pre-generated worlds available for games
     * @return The number of worlds that have been pre-generated but not played on yet
     * @throws GameServerException
     */
    public int getCompletedGenerationNum() throws GameServerException {
        try {
            V1PersistentVolumeClaimList readyWorldList = coreV1Api.listNamespacedPersistentVolumeClaim(NAMESPACE, null, null, null, null, "purpose=beans-world,stage=ready", null, null, null, null, null);
            return readyWorldList.getItems().size();
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private String getAnyReadyClaim() throws GameServerException {
        try {
            V1PersistentVolumeClaimList readyWorldList = coreV1Api.listNamespacedPersistentVolumeClaim(NAMESPACE, null, null, null, null, "purpose=beans-world,stage=ready", null, null, null, null, null);
            return readyWorldList.getItems().size() == 0 ? null : readyWorldList.getItems().get(0).getMetadata().getName();
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private int getGeneratingNum() throws GameServerException {
        try {
            V1JobList generatingJobs = batchV1Api.listNamespacedJob(NAMESPACE, null, null, null, null, "purpose=beans-pre-gen", null, null, null, null, null);
            return generatingJobs.getItems().size();
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    public String createGame(String jarUrl) throws GameServerException {
        String claimName = getAnyReadyClaim();
        if (claimName == null) {
            claimName = createClaim("game");
        } else {
            setClaimStage(claimName, "game");
        }

        String id = getNewGameId();

        V1Job job = new V1JobBuilder()
                .withNewMetadataLike(GAME_JOB.getMetadata())
                    .withName("game-" + Long.hashCode(System.currentTimeMillis()))
                    .addToLabels("game-id", id)
                .endMetadata()
                .withNewSpecLike(GAME_JOB.getSpec())
                    .editTemplate()
                        .editMetadata()
                            .addToLabels("game-id", id)
                        .endMetadata()
                        .editSpec()
                        .editInitContainer(0)
                            .withCommand("wget", jarUrl, GAME_PLUGIN_URL, "-P", "/plugins")
                        .endInitContainer()
                            .editVolume(0)
                                .editPersistentVolumeClaim()
                                    .withClaimName(claimName)
                                .endPersistentVolumeClaim()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        try {
            batchV1Api.createNamespacedJob(NAMESPACE, job, null, null, null, null);
        } catch (ApiException e) {
            throw new GameServerException(e);
        }

        return id;
    }

    /**
     * Get the list of games and their IPs
     * @return Map of games. Key is game ID, value is game IP address. IP is null if game is still starting
     * @throws GameServerException
     */
    public Map<String, InetSocketAddress> getGames() throws GameServerException {
        try {
            Map<String, InetSocketAddress> games = new HashMap<>();

            // Get all games
            V1JobList gameJobs = batchV1Api.listNamespacedJob(NAMESPACE, null, null, null, null, "purpose=beans-game", null, null, null, null, null);
            for (V1Job gameJob : gameJobs.getItems()) {
                String gameId = gameJob.getMetadata().getLabels().get("game-id");
                games.put(gameId, null);
            }

            // Get all running games
            V1PodList gamePods = coreV1Api.listNamespacedPod(NAMESPACE, null, null, null, null, "purpose=beans-game", null, null, null, null, null);
            for (V1Pod gamePod : gamePods.getItems()) {
                if (gamePod.getStatus() != null
                        && gamePod.getStatus().getContainerStatuses() != null
                        && !gamePod.getStatus().getContainerStatuses().get(0).getReady()
                ) {
                    continue;
                }
                String gameId = gamePod.getMetadata().getLabels().get("game-id");
                String podIP = gamePod.getStatus().getPodIP();

                if (podIP == null) {
                    continue;
                }

                int port = gamePod.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();

                games.put(gameId, InetSocketAddress.createUnresolved(podIP, port));
            }

            return games;
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    /**
     * Creates a PVC (Persistent Volume Claim) in kubernetes for storing a minecraft world
     * @param stage Each PVC has a stage that shows what's stored in it
     * @return Name of the PVC
     */
    private String createClaim(String stage) throws GameServerException {
        V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName("beans-world-" + Long.hashCode(System.currentTimeMillis()))
                    .addToLabels("stage", stage)
                    .addToLabels("purpose", "beans-world")
                .endMetadata()
                .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withVolumeMode("Filesystem")
                    .withStorageClassName(STORAGE_CLASS_NAME)
                    .withNewResources()
                        .addToRequests("storage", new Quantity("5Gi"))
                    .endResources()
                .endSpec()
                .build();
        try {
            coreV1Api.createNamespacedPersistentVolumeClaim(NAMESPACE, pvc, null, null, null, null);
            return pvc.getMetadata().getName();
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private void setClaimStage(String name, String stage) throws GameServerException {
        try {
            V1Patch patch = new V1Patch("[{\"op\":\"replace\",\"path\":\"/metadata/labels/stage\",\"value\": \"" + stage + "\"}]");
            coreV1Api.patchNamespacedPersistentVolumeClaim(name, NAMESPACE, patch, null, null, null, null, null);
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private void setClaimGameFinishTimeToNow(String name) throws GameServerException {
        try {
            String timestamp = Long.toString(System.currentTimeMillis());
            V1Patch patch = new V1Patch("[{\"op\":\"add\",\"path\":\"/metadata/labels/game-finish-time\",\"value\": \"" + timestamp + "\"}]");
            coreV1Api.patchNamespacedPersistentVolumeClaim(name, NAMESPACE, patch, null, null, null, null, null);
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }

    private void createPreGenWorld() throws GameServerException {
        String claimName = createClaim("pre-gen");
        V1Job job = new V1JobBuilder()
                .withNewMetadataLike(PRE_GEN_JOB.getMetadata())
                    .withName("pre-gen-" + Long.hashCode(System.currentTimeMillis()))
                .endMetadata()
                .withNewSpecLike(PRE_GEN_JOB.getSpec())
                    .editTemplate()
                        .editSpec()
                            .editVolume(0)
                                .editPersistentVolumeClaim()
                                    .withClaimName(claimName)
                                .endPersistentVolumeClaim()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        try {
            batchV1Api.createNamespacedJob(NAMESPACE, job, null, null, null, null);
        } catch (ApiException e) {
            throw new GameServerException(e);
        }
    }
}
