package org.mcp_server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Container for API response records for list recommendations.
 */
public final class RecommendationApiResponseRecords {
    private RecommendationApiResponseRecords() {}

    // Record for the summary view (without recommendations)
    public record IdleWorkloadInfo(
            String namespace,
            Optional<String> containerName,
            @JsonProperty("experiment_name") String experimentName,
            @JsonProperty("experiment_type") String experimentType
    ) {}

    // Record for the detailed view (with recommendations)
    public record IdleWorkloadWithRecommendations(
            String namespace,
            Optional<String> containerName,
            @JsonProperty("experiment_name") String experimentName,
            @JsonProperty("experiment_type") String experimentType,
            @JsonProperty("recommendation_terms")
            Map<String, RecommendationTermResult> recommendationTerms
    ) {}

    // Final, clean output format for cost recommendations
    public record CostEngineResult(
            String namespace,
            @JsonProperty("container_name")
            Optional<String> containerName,

            @JsonProperty("experiment_name")
            String experimentName,

            @JsonProperty("experiment_type")
            String experimentType,

            List<Notification> notifications,

            @JsonProperty("current")
            ResourceGroup currentUsage,

            @JsonProperty("recommendation_terms")
            Map<String, RecommendationTermResult> recommendationTerms
    ) {}

    // Final, clean output format for performance recommendations
    public record PerformanceEngineResult(
            String namespace,
            @JsonProperty("container_name")
            Optional<String> containerName,

            @JsonProperty("experiment_name")
            String experimentName,

            @JsonProperty("experiment_type")
            String experimentType,

            List<Notification> notifications,

            @JsonProperty("current")
            ResourceGroup currentUsage,

            @JsonProperty("recommendation_terms")
            Map<String, RecommendationTermResult> recommendationTerms
    ) {}
    
    public record RecommendationTermResult(
            @JsonProperty("duration_in_hours")
            double durationInHours,
            @JsonProperty("monitoring_start_time")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String monitoringStartTime,
            @JsonProperty("recommendation_engines")
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, RecommendationEngineData> recommendationEngines,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Notification> notifications
    ) {}


    public record TimestampData(
            ResourceGroup current,
            @JsonProperty("recommendation_terms")
            Map<String, RecommendationTerm> recommendationTerms
    ) {}
    
    public record RecommendationEngineData(
            @JsonProperty("pods_count")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer podsCount,
            @JsonInclude(JsonInclude.Include.NON_ABSENT)
            Optional<Object> config,
            @JsonInclude(JsonInclude.Include.NON_ABSENT)
            Optional<Object> variation,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Notification> notifications
    ) {}

    // --- Records for navigating the JSON structure ---
    public record ResourceMetric(double amount, String format) {}
    
    public record ResourceConfig(
            @JsonInclude(JsonInclude.Include.NON_NULL) ResourceMetric cpu,
            @JsonInclude(JsonInclude.Include.NON_NULL) ResourceMetric memory
    ) {}
    
    public record ResourceGroup(
            @JsonInclude(JsonInclude.Include.NON_NULL) ResourceConfig requests,
            @JsonInclude(JsonInclude.Include.NON_NULL) ResourceConfig limits
    ) {}
    
    // Record for ResourceConfig without CPU (for notification 323001)
    public record ResourceConfigNoCpu(ResourceMetric memory) {}
    public record ResourceGroupNoCpu(ResourceConfigNoCpu requests, ResourceConfigNoCpu limits) {}

    public record RecommendationEngine(
            @JsonProperty("pods_count")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer podsCount,
            ResourceGroup config,
            ResourceGroup variation,
            Map<String, Notification> notifications
    ) {}

    public record RecommendationTerm(
            @JsonProperty("duration_in_hours") int durationInHours,
            @JsonProperty("monitoring_start_time")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String monitoringStartTime,
            @JsonProperty("recommendation_engines") Map<String, RecommendationEngine> recommendationEngines,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Notification> notifications
    ) {}

    public record Notification(String type, String message, int code) {}

    public record RecommendationData(
            String version,
            Map<String, Notification> notifications,
            Map<String, TimestampData> data
    ) {}

    // Namespace record
    public record Namespace(
            @JsonProperty("namespace") String namespace,
            Optional<RecommendationData> recommendations
    ) {}

    // Container record to include recommendations
    public record Container(
            @JsonProperty("container_name") String containerName,
            @JsonProperty("container_image_name") String containerImageName,
            Optional<RecommendationData> recommendations
    ) {}

    public record KubernetesObject(
            String namespace,
            String type,
            String name,
            Optional<List<Container>> containers,
            Optional<Namespace> namespaces
    ) {}

    // Top-level object in the JSON array
    public record Recommendations(
            @JsonProperty("experiment_name") String experimentName,
            @JsonProperty("experiment_type") String experimentType,
            @JsonProperty("kubernetes_objects") List<KubernetesObject> kubernetesObjects
    ) {}

}
