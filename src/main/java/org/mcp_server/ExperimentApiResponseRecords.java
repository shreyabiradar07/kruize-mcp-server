package org.mcp_server;


public final class ExperimentApiResponseRecords {
    private ExperimentApiResponseRecords() {}


    public record Experiment(String experiment_id, String experiment_name, String status, String target_cluster, String mode, String experiment_type) {
    }
}
