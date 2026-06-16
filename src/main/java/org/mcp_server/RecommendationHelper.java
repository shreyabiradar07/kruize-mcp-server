package org.mcp_server;

import org.mcp_server.RecommendationApiResponseRecords.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper utility class for processing recommendation data.
 * Contains methods for handling notification 323001 (idle workload) and resource group transformations.
 */
public class RecommendationHelper {
    
    private static final String IDLE_NOTIFICATION_ID = "323001";
    private static final int IDLE_NOTIFICATION_CODE = 323001;
    
    /**
     * Helper method to check if idle notification (323001) exists in the notifications map
     * 
     * @param notifications Map of notifications to check
     * @return true if notification 323001 exists, false otherwise
     */
    public static boolean hasIdleNotification(Map<String, Notification> notifications) {
        if (notifications == null) {
            return false;
        }
        Notification notice = notifications.get(IDLE_NOTIFICATION_ID);
        return notice != null && notice.code() == IDLE_NOTIFICATION_CODE;
    }
    
    /**
     * Helper method to convert ResourceGroup to ResourceGroupNoCpu by removing CPU fields
     * 
     * @param resourceGroup The resource group to convert
     * @return ResourceGroupNoCpu with CPU fields removed, or null if input is null
     */
    public static ResourceGroupNoCpu removeCpuFromResourceGroup(ResourceGroup resourceGroup) {
        if (resourceGroup == null) return null;
        
        ResourceConfig requests = resourceGroup.requests();
        ResourceConfig limits = resourceGroup.limits();
        
        ResourceConfigNoCpu requestsNoCpu = requests != null
            ? new ResourceConfigNoCpu(requests.memory())
            : null;
            
        ResourceConfigNoCpu limitsNoCpu = limits != null
            ? new ResourceConfigNoCpu(limits.memory())
            : null;
        
        return new ResourceGroupNoCpu(requestsNoCpu, limitsNoCpu);
    }
    
    /**
     * Helper method to build config from a RecommendationEngine, handling 323001 notification
     * by removing CPU fields when present.
     * 
     * @param engine The recommendation engine to extract config from
     * @return Optional containing the config (with CPU removed if 323001 present), or empty if engine is null
     */
    public static Optional<Object> buildConfig(RecommendationEngine engine) {
        if (engine == null) {
            return Optional.empty();
        }

        boolean has323001 = hasIdleNotification(engine.notifications());

        if (has323001) {
            // For 323001, strip CPU fields from the resource group config
            return Optional.ofNullable(engine.config())
                    .map(RecommendationHelper::removeCpuFromResourceGroup);
        }

        return Optional.ofNullable(engine.config())
                .map(rg -> (Object) rg);
    }
    
    /**
     * Helper method to build config and variation from a RecommendationEngine,
     * handling 323001 notification by removing CPU fields when present.
     *
     * @param engine The recommendation engine to extract data from
     * @return A record containing config and variation optionals
     */
    private static EngineConfigVariation buildConfigAndVariation(RecommendationEngine engine) {
        if (engine == null) {
            return new EngineConfigVariation(Optional.empty(), Optional.empty());
        }
        
        boolean has323001 = hasIdleNotification(engine.notifications());
        
        Optional<Object> config;
        Optional<Object> variation;
        
        if (has323001) {
            config = Optional.ofNullable(engine.config())
                    .map(RecommendationHelper::removeCpuFromResourceGroup);
            variation = Optional.ofNullable(engine.variation())
                    .map(RecommendationHelper::removeCpuFromResourceGroup);
        } else {
            config = Optional.ofNullable(engine.config())
                    .map(rg -> (Object) rg);
            variation = Optional.ofNullable(engine.variation())
                    .map(rg -> (Object) rg);
        }
        
        return new EngineConfigVariation(config, variation);
    }
    
    /**
     * Helper record to hold config and variation data
     */
    private record EngineConfigVariation(Optional<Object> config, Optional<Object> variation) {}
    
    /**
     * Helper method to extract notifications from a RecommendationEngine
     *
     * @param engine The recommendation engine
     * @return List of notifications, or empty list if none
     */
    private static List<Notification> extractNotifications(RecommendationEngine engine) {
        return Optional.ofNullable(engine)
                .map(RecommendationEngine::notifications)
                .map(map -> List.copyOf(map.values()))
                .orElse(Collections.emptyList());
    }
    
    /**
     * Helper method to build a WorkloadRecommendationResult from recommendation data.
     * Processes both cost and performance recommendations, handling 323001 notification appropriately.
     *
     * @param experimentName The name of the experiment
     * @param experimentType The type of the experiment
     * @param namespace The namespace of the workload
     * @param workloadType The type of workload (deployment, statefulset, etc.)
     * @param workloadName The name of the workload
     * @param containerName Optional container name
     * @param recommendationData Optional recommendation data
     * @return WorkloadRecommendationResult or null if no recommendation data
     */
    public static WorkloadRecommendationResult buildWorkloadResult(
            String experimentName,
            String experimentType,
            String namespace,
            String workloadType,
            String workloadName,
            Optional<String> containerName,
            Optional<RecommendationData> recommendationData) {
        
        if (recommendationData.isEmpty()) {
            return null;
        }

        RecommendationData recData = recommendationData.get();
        Map<String, TimestampData> dataMap = recData.data();
        
        if (dataMap == null || dataMap.isEmpty()) {
            return new WorkloadRecommendationResult(
                experimentName,
                experimentType,
                namespace,
                workloadType,
                workloadName,
                containerName,
                Optional.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
            );
        }

        TimestampData timestampData = dataMap.values().iterator().next();
        ResourceGroup currentUsage = timestampData.current();
        Map<String, RecommendationTerm> recommendationTerms = timestampData.recommendationTerms();
        
        if (recommendationTerms == null) {
            return new WorkloadRecommendationResult(
                experimentName,
                experimentType,
                namespace,
                workloadType,
                workloadName,
                containerName,
                Optional.ofNullable(currentUsage),
                Collections.emptyList(),
                Collections.emptyList(),
                Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
            );
        }

        List<CostRecommendation> costRecs = recommendationTerms.entrySet().stream()
            .map(termEntry -> {
                String term = termEntry.getKey();
                RecommendationTerm recommendationTerm = termEntry.getValue();

                Map<String, RecommendationEngine> engines = Optional.ofNullable(recommendationTerm.recommendationEngines())
                        .orElse(Collections.emptyMap());
                RecommendationEngine costEngine = engines.get("cost");

                List<Notification> costNotifications = extractNotifications(costEngine);
                EngineConfigVariation configVar = buildConfigAndVariation(costEngine);

                return new CostRecommendation(
                    term,
                    recommendationTerm.durationInHours(),
                    configVar.config(),
                    configVar.variation(),
                    costNotifications.isEmpty() ? Optional.empty() : Optional.of(costNotifications)
                );
            })
            .collect(Collectors.toList());

        List<PerformanceRecommendation> performanceRecs = recommendationTerms.entrySet().stream()
            .map(termEntry -> {
                String term = termEntry.getKey();
                RecommendationTerm recommendationTerm = termEntry.getValue();

                Map<String, RecommendationEngine> engines = Optional.ofNullable(recommendationTerm.recommendationEngines())
                        .orElse(Collections.emptyMap());
                RecommendationEngine performanceEngine = engines.get("performance");

                List<Notification> performanceNotifications = extractNotifications(performanceEngine);
                EngineConfigVariation configVar = buildConfigAndVariation(performanceEngine);

                return new PerformanceRecommendation(
                    term,
                    recommendationTerm.durationInHours(),
                    configVar.config(),
                    configVar.variation(),
                    performanceNotifications
                );
            })
            .collect(Collectors.toList());

        return new WorkloadRecommendationResult(
            experimentName,
            experimentType,
            namespace,
            workloadType,
            workloadName,
            containerName,
            Optional.ofNullable(currentUsage),
            costRecs,
            performanceRecs,
            Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
        );
    }

    /**
     * Helper method to build a WorkloadPerformanceResult from recommendation data.
     * Processes only performance recommendations, handling 323001 notification appropriately.
     *
     * @param experimentName The name of the experiment
     * @param experimentType The type of the experiment
     * @param namespace The namespace of the workload
     * @param workloadType The type of workload (deployment, statefulset, etc.)
     * @param workloadName The name of the workload
     * @param containerName Optional container name
     * @param recommendationData Optional recommendation data
     * @return WorkloadPerformanceResult or null if no recommendation data
     */
    public static WorkloadPerformanceResult buildWorkloadPerformanceResult(
            String experimentName,
            String experimentType,
            String namespace,
            String workloadType,
            String workloadName,
            Optional<String> containerName,
            Optional<RecommendationData> recommendationData) {
        
        if (recommendationData.isEmpty()) {
            return null;
        }

        RecommendationData recData = recommendationData.get();
        Map<String, TimestampData> dataMap = recData.data();
        
        if (dataMap == null || dataMap.isEmpty()) {
            return new WorkloadPerformanceResult(
                experimentName,
                experimentType,
                namespace,
                workloadType,
                workloadName,
                containerName,
                Optional.empty(),
                Collections.emptyMap(),
                Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
            );
        }

        TimestampData timestampData = dataMap.values().iterator().next();
        ResourceGroup currentUsage = timestampData.current();
        Map<String, RecommendationTerm> recommendationTerms = timestampData.recommendationTerms();
        
        if (recommendationTerms == null) {
            return new WorkloadPerformanceResult(
                experimentName,
                experimentType,
                namespace,
                workloadType,
                workloadName,
                containerName,
                Optional.ofNullable(currentUsage),
                Collections.emptyMap(),
                Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
            );
        }

        // Build the recommendation_terms map with performance engine data.
        // If the performance engine is absent, preserve term-level notifications
        // from the API response instead of returning an empty recommendation_engines object.
        Map<String, PerformanceRecommendationTerm> performanceTermsMap = recommendationTerms.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                termEntry -> {
                    RecommendationTerm recommendationTerm = termEntry.getValue();

                    Map<String, RecommendationEngine> engines = Optional.ofNullable(recommendationTerm.recommendationEngines())
                            .orElse(Collections.emptyMap());
                    RecommendationEngine performanceEngine = engines.get("performance");

                    Map<String, PerformanceEngineData> engineDataMap = new java.util.HashMap<>();
                    if (performanceEngine != null) {
                        EngineConfigVariation configVar = buildConfigAndVariation(performanceEngine);

                        PerformanceEngineData perfData = new PerformanceEngineData(
                            configVar.config(),
                            configVar.variation(),
                            Optional.ofNullable(performanceEngine.notifications()).orElse(Collections.emptyMap())
                        );
                        engineDataMap.put("performance", perfData);
                    }

                    Map<String, Notification> termNotifications = Optional.ofNullable(recommendationTerm.notifications())
                        .orElse(Collections.emptyMap());

                    return new PerformanceRecommendationTerm(
                        recommendationTerm.durationInHours(),
                        recommendationTerm.monitoringStartTime(),
                        engineDataMap.isEmpty() ? null : engineDataMap,
                        termNotifications
                    );
                }
            ));

        return new WorkloadPerformanceResult(
            experimentName,
            experimentType,
            namespace,
            workloadType,
            workloadName,
            containerName,
            Optional.ofNullable(currentUsage),
            performanceTermsMap,
            Optional.ofNullable(recData.notifications()).map(map -> List.copyOf(map.values())).orElse(Collections.emptyList())
        );
    }
}
