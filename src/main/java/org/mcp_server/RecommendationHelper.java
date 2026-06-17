package org.mcp_server;

import org.mcp_server.RecommendationApiResponseRecords.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper utility class for processing recommendation data.
 * Contains methods for handling notification 323001 (idle workload) and resource group transformations.
 */
public class RecommendationHelper {
    
    private static final String IDLE_NOTIFICATION_ID = "323001";
    private static final int IDLE_NOTIFICATION_CODE = 323001;
    
    /**
     * Helper record to unify container and namespace recommendation sources
     */
    public record RecommendationSource(
        String parentNamespace,
        Optional<String> sourceName,
        Optional<RecommendationData> recommendations
    ) {}
    
    /**
     * Common function to fetch and process recommendations from Kruize API.
     * This method fetches recommendations from the API client, handles the iteration
     * through the nested structure, and applies a filter predicate to select relevant
     * recommendation sources.
     *
     * @param apiClient The Kruize API client to fetch recommendations from
     * @param filter Predicate to filter recommendation sources (e.g., for idle workloads)
     * @return List of filtered recommendation sources with their context
     */
    public static List<ProcessedRecommendation> processRecommendations(
            org.mcp_server.KruizeApiClient apiClient,
            Predicate<ProcessedRecommendation> filter) {
        
        // Fetch recommendations from Kruize API
        List<Recommendations> apiResponse = apiClient.getAllRecommendations();
        
        if (apiResponse == null || apiResponse.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Pre-allocate with estimated capacity to avoid resizing
        List<ProcessedRecommendation> results = new ArrayList<>(apiResponse.size() * 4);
        
        // Reusable Optional to avoid repeated allocations
        Optional<String> emptyContainerName = Optional.empty();
        
        for (Recommendations recommendations : apiResponse) {
            String experimentName = recommendations.experimentName();
            String experimentType = recommendations.experimentType();
            
            List<KubernetesObject> kubernetesObjects = recommendations.kubernetesObjects();
            if (kubernetesObjects == null || kubernetesObjects.isEmpty()) {
                continue;
            }
            
            for (KubernetesObject k8sObject : kubernetesObjects) {
                String namespace = k8sObject.namespace();
                String type = k8sObject.type();
                String name = k8sObject.name();
                
                // Process containers - optimized path
                Optional<List<Container>> containersOpt = k8sObject.containers();
                if (containersOpt.isPresent()) {
                    List<Container> containers = containersOpt.get();
                    for (Container container : containers) {
                        Optional<RecommendationData> recDataOpt = container.recommendations();
                        if (recDataOpt.isEmpty()) continue;
                        
                        RecommendationData recData = recDataOpt.get();
                        Map<String, TimestampData> dataMap = recData.data();
                        if (dataMap == null || dataMap.isEmpty()) continue;
                        
                        // Get first timestamp data - use entrySet for better performance
                        TimestampData timestampData = dataMap.entrySet().iterator().next().getValue();
                        
                        ProcessedRecommendation processed = new ProcessedRecommendation(
                            experimentName,
                            experimentType,
                            namespace,
                            type,
                            name,
                            Optional.of(container.containerName()),
                            recData,
                            timestampData.current(),
                            timestampData.recommendationTerms()
                        );
                        
                        if (filter.test(processed)) {
                            results.add(processed);
                        }
                    }
                }
                
                // Process namespaces - optimized path
                Optional<Namespace> namespaceOpt = k8sObject.namespaces();
                if (namespaceOpt.isPresent()) {
                    Namespace namespaceObj = namespaceOpt.get();
                    Optional<RecommendationData> recDataOpt = namespaceObj.recommendations();
                    if (recDataOpt.isEmpty()) continue;
                    
                    RecommendationData recData = recDataOpt.get();
                    Map<String, TimestampData> dataMap = recData.data();
                    if (dataMap == null || dataMap.isEmpty()) continue;
                    
                    TimestampData timestampData = dataMap.entrySet().iterator().next().getValue();
                    
                    ProcessedRecommendation processed = new ProcessedRecommendation(
                        experimentName,
                        experimentType,
                        namespaceObj.namespace(),
                        type,
                        name,
                        emptyContainerName,
                        recData,
                        timestampData.current(),
                        timestampData.recommendationTerms()
                    );
                    
                    if (filter.test(processed)) {
                        results.add(processed);
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * Creates a predicate to filter recommendations by container name and optional namespace.
     *
     * @param containerName The container name to filter by (required, case-insensitive)
     * @param namespace The namespace to filter by (optional, case-insensitive)
     * @return A predicate that filters ProcessedRecommendation objects
     */
    public static java.util.function.Predicate<ProcessedRecommendation>
            createContainerNamespaceFilter(String containerName, String namespace) {
        return processed -> {
            // Filter by container name (must match)
            if (processed.containerName().isEmpty() ||
                !processed.containerName().get().equalsIgnoreCase(containerName.trim())) {
                return false;
            }
            
            // Filter by namespace if provided
            if (namespace != null && !namespace.trim().isEmpty() &&
                !processed.namespace().equalsIgnoreCase(namespace.trim())) {
                return false;
            }
            
            return true;
        };
    }
    
    /**
     * Record to hold processed recommendation data with all necessary context
     */
    public record ProcessedRecommendation(
        String experimentName,
        String experimentType,
        String namespace,
        String workloadType,
        String workloadName,
        Optional<String> containerName,
        RecommendationData recommendationData,
        ResourceGroup currentUsage,
        Map<String, RecommendationTerm> recommendationTerms
    ) {}
    
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

    @FunctionalInterface
    private interface TermResultBuilder {
        RecommendationTermResult build(
                RecommendationTerm recommendationTerm,
                Map<String, RecommendationEngineData> recommendationEngines,
                Map<String, Notification> notifications
        );
    }

    @FunctionalInterface
    private interface EngineResultBuilder<T> {
        T build(
                ProcessedRecommendation processed,
                List<Notification> notifications,
                Map<String, RecommendationTermResult> recommendationTerms
        );
    }

    private static List<Notification> extractNotifications(ProcessedRecommendation processed) {
        Map<String, Notification> notifications = processed.recommendationData().notifications();
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }
        // Direct list creation is faster than ArrayList copy
        return List.copyOf(notifications.values());
    }

    private static RecommendationEngineData buildRecommendationEngineData(RecommendationEngine engine) {
        if (engine == null) {
            return null;
        }

        EngineConfigVariation configVar = buildConfigAndVariation(engine);
        return new RecommendationEngineData(
                engine.podsCount(),
                engine.confidenceLevel() != null ? engine.confidenceLevel() : 0.0,
                configVar.config(),
                configVar.variation(),
                Optional.ofNullable(engine.notifications()).orElse(Collections.emptyMap())
        );
    }

    private static Map<String, RecommendationTermResult> buildRecommendationTermsMap(
            ProcessedRecommendation processed,
            String engineKey,
            TermResultBuilder termResultBuilder) {
        return buildRecommendationTermsMap(processed, List.of(engineKey), termResultBuilder);
    }

    private static Map<String, RecommendationTermResult> buildRecommendationTermsMap(
            ProcessedRecommendation processed,
            List<String> engineKeys,
            TermResultBuilder termResultBuilder) {
        Map<String, RecommendationTerm> terms = processed.recommendationTerms();
        if (terms == null || terms.isEmpty()) {
            return Collections.emptyMap();
        }

        // Use LinkedHashMap to preserve insertion order from API
        Map<String, RecommendationTermResult> result = new java.util.LinkedHashMap<>(terms.size());
        
        for (Map.Entry<String, RecommendationTerm> termEntry : terms.entrySet()) {
            RecommendationTerm recommendationTerm = termEntry.getValue();
            
            Map<String, RecommendationEngine> engines = recommendationTerm.recommendationEngines();
            
            // Build engine data map efficiently
            Map<String, RecommendationEngineData> recommendationEngines = null;
            if (engines != null && !engines.isEmpty()) {
                recommendationEngines = new java.util.HashMap<>(engineKeys.size());
                for (String engineKey : engineKeys) {
                    RecommendationEngine engine = engines.get(engineKey);
                    if (engine != null) {
                        RecommendationEngineData data = buildRecommendationEngineData(engine);
                        if (data != null) {
                            recommendationEngines.put(engineKey, data);
                        }
                    }
                }
                if (recommendationEngines.isEmpty()) {
                    recommendationEngines = null;
                }
            }
            
            Map<String, Notification> termNotifications = recommendationTerm.notifications();
            if (termNotifications == null) {
                termNotifications = Collections.emptyMap();
            }
            
            result.put(termEntry.getKey(), termResultBuilder.build(
                    recommendationTerm,
                    recommendationEngines,
                    termNotifications
            ));
        }
        
        return result;
    }

    private static RecommendationTermResult buildRecommendationTermResult(
            RecommendationTerm recommendationTerm,
            Map<String, RecommendationEngineData> recommendationEngines,
            Map<String, Notification> notifications) {
        return new RecommendationTermResult(
                recommendationTerm.durationInHours(),
                recommendationTerm.monitoringStartTime(),
                recommendationEngines,
                notifications
        );
    }

    private static <T> T buildEngineResult(
            ProcessedRecommendation processed,
            String engineKey,
            EngineResultBuilder<T> resultBuilder) {
        List<Notification> notifications = extractNotifications(processed);
        Map<String, RecommendationTermResult> recommendationTermsMap = buildRecommendationTermsMap(
                processed,
                engineKey,
                RecommendationHelper::buildRecommendationTermResult
        );

        return resultBuilder.build(processed, notifications, recommendationTermsMap);
    }
    
    /**
     * Extract cost recommendations from a ProcessedRecommendation.
     *
     * @param processed The processed recommendation to extract cost data from
     * @return CostEngineResult containing cost recommendations
     */
    public static CostEngineResult extractCostRecommendations(ProcessedRecommendation processed) {
        return buildEngineResult(
                processed,
                "cost",
                (recommendation, notifications, recommendationTerms) -> new CostEngineResult(
                        recommendation.namespace(),
                        recommendation.containerName(),
                        recommendation.experimentName(),
                        recommendation.experimentType(),
                        notifications,
                        recommendation.currentUsage(),
                        recommendationTerms
                )
        );
    }
    
    /**
     * Extract performance recommendations from a ProcessedRecommendation.
     * Returns a PerformanceEngineResult with performance-specific data using the same
     * nested structure as cost recommendations.
     *
     * @param processed The processed recommendation to extract performance data from
     * @return PerformanceEngineResult containing performance recommendations
     */
    public static PerformanceEngineResult extractPerformanceRecommendations(ProcessedRecommendation processed) {
        return buildEngineResult(
                processed,
                "performance",
                (recommendation, notifications, recommendationTerms) -> new PerformanceEngineResult(
                        recommendation.namespace(),
                        recommendation.containerName(),
                        recommendation.experimentName(),
                        recommendation.experimentType(),
                        notifications,
                        recommendation.currentUsage(),
                        recommendationTerms
                )
        );
    }
    
    /**
     * Extract idle workload information from a ProcessedRecommendation.
     * This is a simple wrapper that pulls out idle workload data.
     *
     * @param processed The processed recommendation to extract idle data from
     * @param includeRecommendations Whether to include detailed cost recommendations
     * @return IdleWorkloadInfo or IdleWorkloadWithRecommendations based on parameter
     */
    public static Object extractIdleWorkload(ProcessedRecommendation processed, boolean includeRecommendations) {
        if (includeRecommendations) {
            Map<String, RecommendationTermResult> recommendationTermsMap = buildRecommendationTermsMap(
                    processed,
                    List.of("cost", "performance"),
                    RecommendationHelper::buildRecommendationTermResult
            );

            return new IdleWorkloadWithRecommendations(
                processed.namespace(),
                processed.containerName(),
                processed.experimentName(),
                processed.experimentType(),
                recommendationTermsMap
            );
        } else {
            return new IdleWorkloadInfo(
                processed.namespace(),
                processed.containerName(),
                processed.experimentName(),
                processed.experimentType()
            );
        }
    }
}
