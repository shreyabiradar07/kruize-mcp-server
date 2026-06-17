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

    @Tool(description = "List all experiments")
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

    @Tool(description = "List resource optimization CPU/memory recommendations for all the containers")
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


    @Tool(description = "Get cost-optimized CPU/memory recommendations for a container. Optionally by namespace")
    @Blocking
    public String getCostOptimizedRecommendations(
            @ToolArg(description = "Container name")
            @NotBlank(message = "Container name cannot be empty")
            String containerName,
            @ToolArg(description = "Namespace")
            String namespace) {
        try {
            log.info("Fetching cost recommendations for container: {}, namespace: {}",
                    containerName, namespace != null ? namespace : "all");

            // Get matching recommendations and extract cost data using simple wrapper
            List<RecommendationHelper.ProcessedRecommendation> matchingRecs =
                RecommendationHelper.processRecommendations(apiClient,
                    RecommendationHelper.createContainerNamespaceFilter(containerName, namespace));
            
            if (matchingRecs.isEmpty()) {
                String namespaceMsg = namespace != null && !namespace.trim().isEmpty()
                    ? " in namespace '" + namespace + "'"
                    : " across all namespaces";
                return "{\"message\": \"No cost recommendations found for container '"
                    + containerName + "'" + namespaceMsg + ".\"}";
            }

            // Pre-allocate list and avoid stream overhead
            List<CostEngineResult> matchingResults = new ArrayList<>(matchingRecs.size());
            for (RecommendationHelper.ProcessedRecommendation rec : matchingRecs) {
                matchingResults.add(RecommendationHelper.extractCostRecommendations(rec));
            }

            return objectMapper.writeValueAsString(matchingResults);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendation data", e);
            return "{\"error\": \"Failed to serialize recommendation data to JSON: " + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("Failed to retrieve cost recommendations for container", e);
            return "{\"error\": \"Failed to retrieve recommendations: " + e.getMessage() + "\"}";
        }
    }

    // Helper record to pass matching sources internally
    @Tool(description = "Get idle workloads (CPU usage < 1 millicore). Optionally with cost and performance recommendations")
    @Blocking
    public String getIdleWorkloads(
            @ToolArg(description = "Include cost and performance recommendations")
            boolean includeRecommendations) {
        try {
            // Get idle workloads and extract data using simple wrapper
            List<RecommendationHelper.ProcessedRecommendation> idleWorkloads =
                RecommendationHelper.processRecommendations(apiClient, processed -> {
                    Map<String, RecommendationTerm> terms = processed.recommendationTerms();
                    if (terms == null || terms.isEmpty()) return false;
                    
                    // Check if any term has the idle notification (323001) - optimized iteration
                    for (RecommendationTerm term : terms.values()) {
                        Map<String, RecommendationEngine> engines = term.recommendationEngines();
                        if (engines != null) {
                            RecommendationEngine costEngine = engines.get("cost");
                            if (costEngine != null && hasIdleNotification(costEngine.notifications())) {
                                return true;
                            }
                        }
                    }
                    return false;
                });
            
            // Pre-allocate and avoid stream overhead
            List<Object> results = new ArrayList<>(idleWorkloads.size());
            for (RecommendationHelper.ProcessedRecommendation workload : idleWorkloads) {
                results.add(RecommendationHelper.extractIdleWorkload(workload, includeRecommendations));
            }
            
            return objectMapper.writeValueAsString(results);
            
        } catch (Exception e) {
            return "{\"error\": \"An unexpected error occurred: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Get performance-optimized CPU/memory recommendations for a container. Optionally by namespace")
    @Blocking
    public String getPerformanceOptimizedRecommendations(
            @ToolArg(description = "Container name")
            @NotBlank(message = "Container name cannot be empty")
            String containerName,
            @ToolArg(description = "Namespace")
            String namespace) {
        try {
            log.info("Fetching performance recommendations for container: {}, namespace: {}",
                    containerName, namespace != null ? namespace : "all");

            // Get matching recommendations and extract performance data using simple wrapper
            List<RecommendationHelper.ProcessedRecommendation> matchingRecs =
                RecommendationHelper.processRecommendations(apiClient,
                    RecommendationHelper.createContainerNamespaceFilter(containerName, namespace));
            
            if (matchingRecs.isEmpty()) {
                String namespaceMsg = namespace != null && !namespace.trim().isEmpty()
                    ? " in namespace '" + namespace + "'"
                    : " across all namespaces";
                return "{\"message\": \"No performance recommendations found for container '"
                    + containerName + "'" + namespaceMsg + ".\"}";
            }

            // Pre-allocate list and avoid stream overhead
            List<PerformanceEngineResult> matchingResults = new ArrayList<>(matchingRecs.size());
            for (RecommendationHelper.ProcessedRecommendation rec : matchingRecs) {
                matchingResults.add(RecommendationHelper.extractPerformanceRecommendations(rec));
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
