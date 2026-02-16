package org.mcp_server;

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
            List<CostRecommendation> costRecommendations
    ) {}

    // Final, clean output format for cost optimized recommendations
    public record FinalCostResult(
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

            @JsonProperty("cost")
            List<CostRecommendation> costRecommendations
    ) {}


    public record TimestampData(
            ResourceGroup current,
            @JsonProperty("recommendation_terms")
            Map<String, RecommendationTerm> recommendationTerms
    ) {}

    public record CostRecommendation(
            String term,

            @JsonProperty("duration_in_hours")
            int durationInHours,
            Optional<ResourceGroup> config,
            Optional<ResourceGroup> variation,
            Optional<List<Notification>> notifications
    ) {}

    // --- Records for navigating the JSON structure ---
    public record ResourceMetric(double amount, String format) {}
    public record ResourceConfig(ResourceMetric cpu, ResourceMetric memory) {}
    public record ResourceGroup(ResourceConfig requests, ResourceConfig limits) {}

    public record RecommendationEngine(
            ResourceGroup config,
            ResourceGroup variation,
             Map<String, Notification> notifications
    ) {}

    public record RecommendationTerm(
            @JsonProperty("duration_in_hours") int durationInHours,
            @JsonProperty("recommendation_engines") Map<String, RecommendationEngine> recommendationEngines
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