package org.mcp_server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import io.smallrye.common.annotation.Blocking;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mcp_server.ExperimentApiResponseRecords.Experiment;
import org.mcp_server.RecommendationApiResponseRecords.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mcp_server.RecommendationHelper.*;

public class KruizeTools {
    private static final Logger log = LoggerFactory.getLogger(KruizeTools.class);

    @Inject
    @RestClient
    KruizeApiClient apiClient;

    @Inject
    ObjectMapper objectMapper;

    @Tool(description = "Retrieves a list of all available experiments.")
    @Blocking
    public String listAllExperiments() {
        try {
            List<Experiment> experiments = apiClient.getAllExperiments();

            if (experiments == null || experiments.isEmpty()) {
                // Return a valid empty JSON array
                return "[]";
            }

            return objectMapper.writeValueAsString(experiments);

        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize experiment data to JSON.\"}";
        } catch (Exception e) {
            return "{\"error\": \"Failed to retrieve experiments from the API.\"}";
        }
    }

    @Tool(description = "Retrieves a list of all available recommendations.")
    @Blocking
    public String listAllRecommendations() {
        try {
            List<Recommendations> apiResponse = apiClient.getAllRecommendations(); // Pass null for no name filter

            if (apiResponse == null) {
                return "[]";
            }

            String jsonOutput = objectMapper.writeValueAsString(apiResponse);
            return jsonOutput;

        } catch (Exception e) {
            return "{\"error\": \"Failed to retrieve recommendations from the API: " + e.getMessage() + "\"}";
        }
    }


    private record RecommendationSource(String parentNamespace, Optional<String> sourceName, Optional<RecommendationData> recommendations) {}

    @Tool(description = "Retrieves available cost optimized recommendations.")
    @Blocking
    public String getCostOptimizedRecommendations() {
        try {
            List<Recommendations> apiResponse = apiClient.getAllRecommendations();
            List<FinalCostResult> allFinalResults = new ArrayList<>();

            // 1. Loop through each Recommendation object in the API response list
            for (Recommendations recommendations : apiResponse) {

                String experimentName = recommendations.experimentName();
                String experimentType = recommendations.experimentType();

                // 2. Process the kubernetes_objects for the current experiment
                List<KubernetesObject> kubernetesObjects = Optional.ofNullable(recommendations.kubernetesObjects())
                        .orElse(Collections.emptyList());

                for (KubernetesObject k8sObject : kubernetesObjects) {

                    // 3. Create the unified stream of containers and namespaces
                    Stream<RecommendationSource> sourceStream = Stream.concat(
                            k8sObject.containers().orElse(Collections.emptyList()).stream()
                                    .map(c -> new RecommendationSource(k8sObject.namespace(), Optional.of(c.containerName()), c.recommendations())),
                            k8sObject.namespaces().stream()
                                    .map(n -> new RecommendationSource(n.namespace(), Optional.empty(), n.recommendations()))
                    );

                    // 4. Map the sources to the final result, now with easy access to parent fields
                    sourceStream
                            .map(source -> {
                                if (source.recommendations().isEmpty()) return null;

                                List<Notification> notifications = Optional.ofNullable(source.recommendations.get().notifications())
                                        .map(map -> List.copyOf(map.values()))
                                        .orElse(Collections.emptyList());


                                Map<String, TimestampData> dataMap = source.recommendations().get().data();
                                if (dataMap == null || dataMap.isEmpty()) {
                                    return new FinalCostResult(
                                            source.parentNamespace(),
                                            source.sourceName(),
                                            experimentName,
                                            experimentType,
                                            notifications,
                                            null,
                                            Collections.emptyList() // No cost recommendations
                                    );
                                }

                                TimestampData timestampData = dataMap.values().iterator().next();
                                ResourceGroup currentUsage = timestampData.current();
                                Map<String, RecommendationTerm> recommendationTerms = timestampData.recommendationTerms();
                                if (recommendationTerms == null) {
                                    return new FinalCostResult(
                                            source.parentNamespace(),
                                            source.sourceName(),
                                            experimentName,
                                            experimentType,
                                            notifications,
                                            currentUsage,
                                            Collections.emptyList() // No currentUsage
                                    );
                                }

                                List<CostRecommendation> costRecs = recommendationTerms.entrySet().stream()
                                        .map(termEntry -> {
                                            String term = termEntry.getKey();
                                            RecommendationTerm recommendationTerm = termEntry.getValue();

                                            Map<String, RecommendationEngine> engines = Optional.ofNullable(recommendationTerm.recommendationEngines()).orElse(Collections.emptyMap());
                                            RecommendationEngine costEngine = engines.get("cost");

                                            List<Notification> costNotifications = Optional.ofNullable(costEngine)
                                                    .map(RecommendationEngine::notifications)
                                                    .map(map -> List.copyOf(map.values()))
                                                    .orElse(Collections.emptyList());

                                            // Use helper to build config with 323001 handling
                                            Optional<Object> config = buildConfig(costEngine);
                                            
                                            // Check if idle notification exists for variation handling
                                            boolean has323001 = costEngine != null && hasIdleNotification(costEngine.notifications());
                                            Optional<Object> variation;
                                            
                                            if (has323001) {
                                                variation = Optional.ofNullable(costEngine)
                                                    .map(RecommendationEngine::variation)
                                                    .map(RecommendationHelper::removeCpuFromResourceGroup);
                                            } else {
                                                variation = Optional.ofNullable(costEngine)
                                                    .map(RecommendationEngine::variation)
                                                    .map(rg -> (Object) rg);
                                            }

                                            return new CostRecommendation(
                                                    term,
                                                    recommendationTerm.durationInHours(),
                                                    config,
                                                    variation,
                                                    costNotifications.isEmpty() ? Optional.empty() : Optional.of(costNotifications)
                                            );
                                        })
                                        .collect(Collectors.toList());

                                // Use the data from the unified 'source' object
                                return new FinalCostResult(
                                        source.parentNamespace(),
                                        source.sourceName(),
                                        experimentName,
                                        experimentType,
                                        notifications,
                                        currentUsage,
                                        costRecs
                                );
                            })
                            .filter(java.util.Objects::nonNull)
                            .forEach(allFinalResults::add);
                }
            }

            return objectMapper.writeValueAsString(allFinalResults);

        } catch (Exception e) {
            return "{\"error\": \"An unexpected error occurred: " + e.getMessage() + "\"}";
        }
    }

    // Helper record to pass matching sources internally
    private record IdleSource(Recommendations recommendations, RecommendationSource source, Map<String, RecommendationTerm> recommendationTerms) {}

    @Tool(description = "Retrieves idle workloads based on notification code 323001. Optionally includes cost recommendations data.")
    @Blocking
    public String getIdleWorkloads(
            @ToolArg(description = "Set to 'true' to include detailed cost recommendations in the response.")
            boolean includeRecommendations) {
        try {
            List<Recommendations> apiResponse = apiClient.getAllRecommendations();

            List<IdleSource> idleSources = new ArrayList<>();

            for (Recommendations recommendations : Optional.ofNullable(apiResponse).orElse(Collections.emptyList())) {
                for (KubernetesObject k8sObject : Optional.ofNullable(recommendations.kubernetesObjects()).orElse(Collections.emptyList())) {

                    Stream<RecommendationSource> sourceStream = Stream.concat(
                            k8sObject.containers().orElse(Collections.emptyList()).stream()
                                    .map(c -> new RecommendationSource(k8sObject.namespace(), Optional.of(c.containerName()), c.recommendations())),
                            k8sObject.namespaces().stream()
                                    .map(n -> new RecommendationSource(n.namespace(), Optional.empty(), n.recommendations()))
                    );

                    sourceStream.forEach(source -> {
                        if (source.recommendations().isEmpty()) return;

                        Map<String, TimestampData> dataMap = source.recommendations().get().data();
                        if (dataMap == null || dataMap.isEmpty()) return;

                        TimestampData timestampData = dataMap.values().iterator().next();
                        Map<String, RecommendationTerm> recommendationTerms = timestampData.recommendationTerms();
                        if (recommendationTerms == null) return;

                        boolean hasIdleNotice = recommendationTerms.values().stream()
                                .anyMatch(term -> {
                                    RecommendationEngine costEngine = Optional.ofNullable(term.recommendationEngines())
                                            .orElse(Collections.emptyMap()).get("cost");

                                    return costEngine != null && hasIdleNotification(costEngine.notifications());
                                });

                        if (hasIdleNotice) {
                            idleSources.add(new IdleSource(recommendations, source, recommendationTerms));
                        }
                    });
                }
            }

            if (includeRecommendations) {
                List<IdleWorkloadWithRecommendations> detailedResults = idleSources.stream()
                        .map(idleSource -> {
                            List<CostRecommendation> costRecs = idleSource.recommendationTerms().entrySet().stream()
                                    .map(entry -> {
                                        RecommendationEngine costEngine = Optional.ofNullable(entry.getValue().recommendationEngines())
                                                .orElse(Collections.emptyMap()).get("cost");

                                        List<Notification> costNotifications = Optional.ofNullable(costEngine)
                                                .map(RecommendationEngine::notifications)
                                                .map(map -> List.copyOf(map.values()))
                                                .orElse(Collections.emptyList());

                                        // Use helper to build config with 323001 handling
                                        Optional<Object> config = buildConfig(costEngine);
                                        
                                        // Check if idle notification exists for variation handling
                                        boolean has323001 = costEngine != null && hasIdleNotification(costEngine.notifications());
                                        Optional<Object> variation;
                                        
                                        if (has323001) {
                                            variation = Optional.ofNullable(costEngine)
                                                .map(RecommendationEngine::variation)
                                                .map(RecommendationHelper::removeCpuFromResourceGroup);
                                        } else {
                                            variation = Optional.ofNullable(costEngine)
                                                .map(RecommendationEngine::variation)
                                                .map(rg -> (Object) rg);
                                        }

                                        return new CostRecommendation(
                                                entry.getKey(),
                                                entry.getValue().durationInHours(),
                                                config,
                                                variation,
                                                costNotifications.isEmpty() ? Optional.empty() : Optional.of(costNotifications)
                                        );
                                    })
                                    .collect(Collectors.toList());

                            return new IdleWorkloadWithRecommendations(
                                    idleSource.source().parentNamespace(),
                                    idleSource.source().sourceName(),
                                    idleSource.recommendations().experimentName(),
                                    idleSource.recommendations().experimentType(),
                                    costRecs
                            );
                        })
                        .collect(Collectors.toList());
                return objectMapper.writeValueAsString(detailedResults);
            } else {
                List<IdleWorkloadInfo> summaryResults = idleSources.stream()
                        .map(idleSource -> new IdleWorkloadInfo(
                                idleSource.source().parentNamespace(),
                                idleSource.source().sourceName(),
                                idleSource.recommendations().experimentName(),
                                idleSource.recommendations().experimentType()
                        ))
                        .collect(Collectors.toList());
                return objectMapper.writeValueAsString(summaryResults);
            }

        } catch (Exception e) {
            return "{\"error\": \"An unexpected error occurred: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Get performance recommendations for a container. Optionally filter by namespace.")
    @Blocking
    public String getPerformanceRecommendations(
            @ToolArg(description = "Container name")
            @NotBlank(message = "Container name cannot be empty")
            String containerName,
            @ToolArg(description = "Namespace")
            String namespace) {
        try {
            log.info("Fetching performance recommendations for container: {}, namespace: {}",
                    containerName, namespace != null ? namespace : "all");
            
            // Fetch all recommendations
            List<Recommendations> allRecommendations = apiClient.getAllRecommendations();
            
            if (allRecommendations == null || allRecommendations.isEmpty()) {
                return "{\"message\": \"No recommendations found in the system.\"}";
            }

            List<WorkloadPerformanceResult> matchingResults = new ArrayList<>();
            
            // Iterate through all recommendations to find matches
            for (Recommendations recommendations : allRecommendations) {
                String experimentName = recommendations.experimentName();
                String experimentType = recommendations.experimentType();
                
                List<KubernetesObject> kubernetesObjects = Optional.ofNullable(recommendations.kubernetesObjects())
                        .orElse(Collections.emptyList());
                
                for (KubernetesObject k8sObject : kubernetesObjects) {
                    // Check if namespace filter is provided and matches
                    if (namespace != null && !namespace.trim().isEmpty()
                            && !k8sObject.namespace().equalsIgnoreCase(namespace.trim())) {
                        continue; // Skip if namespace doesn't match
                    }
                    
                    // Check containers for matching container name
                    List<Container> containers = k8sObject.containers().orElse(Collections.emptyList());
                    
                    for (Container container : containers) {
                        if (container.containerName().equalsIgnoreCase(containerName.trim())) {
                            // Found a matching container
                            WorkloadPerformanceResult result = RecommendationHelper.buildWorkloadPerformanceResult(
                                experimentName,
                                experimentType,
                                k8sObject.namespace(),
                                k8sObject.type(),
                                k8sObject.name(),
                                Optional.of(container.containerName()),
                                container.recommendations()
                            );
                            
                            if (result != null) {
                                matchingResults.add(result);
                            }
                        }
                    }
                }
            }
            
            if (matchingResults.isEmpty()) {
                String namespaceMsg = namespace != null && !namespace.trim().isEmpty()
                    ? " in namespace '" + namespace + "'"
                    : " across all namespaces";
                return "{\"message\": \"No performance recommendations found for container '"
                    + containerName + "'" + namespaceMsg + ".\"}";
            }

            return objectMapper.writeValueAsString(matchingResults);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendation data", e);
            return "{\"error\": \"Failed to serialize recommendation data to JSON: " + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("Failed to retrieve recommendations for container", e);
            return "{\"error\": \"Failed to retrieve recommendations: " + e.getMessage() + "\"}";
        }
    }

}
