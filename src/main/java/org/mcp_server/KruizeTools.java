package org.mcp_server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import jakarta.inject.Inject;
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
                                                    Optional.of(costNotifications)
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
                                                Optional.of(costNotifications)
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

    @Tool(description = "Retrieves workload recommendations by name, type, namespace, and/or container name.")
    @Blocking
    public String listRecommendationsForWorkload(
            @ToolArg(description = "Workload name", required = false)
            String workloadName,
            @ToolArg(description = "Workload type", required = false)
            String workloadType,
            @ToolArg(description = "Namespace", required = false)
            String namespace,
            @ToolArg(description = "Container name", required = false)
            String containerName) {
        try {
            List<Recommendations> apiResponse;
            
            // Optimization: If we have enough criteria to identify an experiment, try to find it first
            // and fetch recommendations directly for that experiment
            if ((workloadName != null && !workloadName.trim().isEmpty()) &&
                (workloadType != null && !workloadType.trim().isEmpty()) &&
                (namespace != null && !namespace.trim().isEmpty())) {
                
                // Get all experiments to find matching experiment name
                List<Experiment> experiments = apiClient.getAllExperiments();
                
                if (experiments != null && !experiments.isEmpty()) {
                    // Find matching experiment(s)
                    // Experiment name format: datasource|cluster|namespace|workload_type|workload_name
                    String matchingExperimentName = null;
                    
                    for (Experiment exp : experiments) {
                        String expName = exp.experiment_name();
                        if (expName == null) continue;
                        
                        String[] expParts = expName.split("\\|");
                        if (expParts.length < 5) continue;
                        
                        String expNamespace = expParts[2];
                        String expWorkloadType = expParts[3];
                        String expWorkloadName = expParts[4];
                        
                        if (expNamespace.equalsIgnoreCase(namespace.trim()) &&
                            expWorkloadType.equalsIgnoreCase(workloadType.trim()) &&
                            expWorkloadName.equalsIgnoreCase(workloadName.trim())) {
                            matchingExperimentName = expName;
                            break;
                        }
                    }
                    
                    // If we found a matching experiment, fetch recommendations for it directly
                    if (matchingExperimentName != null) {
                        log.info("Found matching experiment: {}. Fetching recommendations directly.", matchingExperimentName);
                        apiResponse = apiClient.getRecommendationsByExperiment(matchingExperimentName);
                    } else {
                        // No matching experiment found, fall back to getting all recommendations
                        log.info("No matching experiment found. Fetching all recommendations.");
                        apiResponse = apiClient.getAllRecommendations();
                    }
                } else {
                    // No experiments available, fall back to getting all recommendations
                    apiResponse = apiClient.getAllRecommendations();
                }
            } else {
                // Insufficient criteria to identify a specific experiment, get all recommendations
                apiResponse = apiClient.getAllRecommendations();
            }
            
            if (apiResponse == null || apiResponse.isEmpty()) {
                return "{\"message\": \"No recommendations found in the system.\"}";
            }

            List<WorkloadRecommendationResult> matchingResults = new ArrayList<>();

            // Iterate through all recommendations
            for (Recommendations recommendations : apiResponse) {
                String experimentName = recommendations.experimentName();
                String experimentType = recommendations.experimentType();
                
                // Parse experiment name: datasource|cluster|namespace|workload_type|workload_name
                // datasource: prometheus-1 (minikube/kind) or thanos-1 (openshift)
                String[] expParts = experimentName != null ? experimentName.split("\\|") : new String[0];
                
                if (expParts.length < 5) continue;
                
                String expNamespace = expParts[2];
                String expWorkloadType = expParts[3];
                String expWorkloadName = expParts[4];
                
                // Check if experiment matches the search criteria
                boolean namespaceMatch = namespace == null || namespace.trim().isEmpty() ||
                                        expNamespace.equalsIgnoreCase(namespace.trim());
                boolean workloadTypeMatch = workloadType == null || workloadType.trim().isEmpty() ||
                                           expWorkloadType.equalsIgnoreCase(workloadType.trim());
                boolean workloadNameMatch = workloadName == null || workloadName.trim().isEmpty() ||
                                           expWorkloadName.equalsIgnoreCase(workloadName.trim());
                
                if (!namespaceMatch || !workloadTypeMatch || !workloadNameMatch) {
                    continue;
                }

                // Process kubernetes objects
                List<KubernetesObject> kubernetesObjects = Optional.ofNullable(recommendations.kubernetesObjects())
                        .orElse(Collections.emptyList());

                for (KubernetesObject k8sObject : kubernetesObjects) {
                    // Process containers if container name filter is provided
                    if (containerName != null && !containerName.trim().isEmpty()) {
                        List<Container> containers = k8sObject.containers().orElse(Collections.emptyList());
                        
                        for (Container container : containers) {
                            if (container.containerName().equalsIgnoreCase(containerName.trim())) {
                                WorkloadRecommendationResult result = RecommendationHelper.buildWorkloadResult(
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
                    } else {
                        // No container filter - include all containers
                        List<Container> containers = k8sObject.containers().orElse(Collections.emptyList());
                        
                        for (Container container : containers) {
                            WorkloadRecommendationResult result = RecommendationHelper.buildWorkloadResult(
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
                        
                        // Also check namespace-level recommendations
                        Optional<Namespace> namespaceOpt = k8sObject.namespaces();
                        if (namespaceOpt.isPresent()) {
                            Namespace ns = namespaceOpt.get();
                            WorkloadRecommendationResult result = RecommendationHelper.buildWorkloadResult(
                                experimentName,
                                experimentType,
                                k8sObject.namespace(),
                                k8sObject.type(),
                                k8sObject.name(),
                                Optional.empty(),
                                ns.recommendations()
                            );
                            if (result != null) {
                                matchingResults.add(result);
                            }
                        }
                    }
                }
            }

            if (matchingResults.isEmpty()) {
                StringBuilder criteria = new StringBuilder("No recommendations found for workload with criteria: ");
                if (workloadName != null && !workloadName.trim().isEmpty()) {
                    criteria.append("name='").append(workloadName).append("' ");
                }
                if (workloadType != null && !workloadType.trim().isEmpty()) {
                    criteria.append("type='").append(workloadType).append("' ");
                }
                if (namespace != null && !namespace.trim().isEmpty()) {
                    criteria.append("namespace='").append(namespace).append("' ");
                }
                if (containerName != null && !containerName.trim().isEmpty()) {
                    criteria.append("container='").append(containerName).append("' ");
                }
                return "{\"message\": \"" + criteria.toString().trim() + "\"}";
            }

            return objectMapper.writeValueAsString(matchingResults);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendation data", e);
            return "{\"error\": \"Failed to serialize recommendation data to JSON: " + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("Failed to retrieve recommendations for workload", e);
            return "{\"error\": \"Failed to retrieve recommendations: " + e.getMessage() + "\"}";
        }
    }

}
