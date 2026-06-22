package org.mcp_server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Readiness health check for Kruize API connectivity.
 * This check verifies that the Kruize backend API is accessible and responding
 * using the /health endpoint.
 *
 * When this check fails:
 * - The pod is marked as NOT READY
 * - Kubernetes removes the pod from service endpoints
 * - No traffic is routed to this pod
 * - The pod is NOT restarted (unlike liveness probe failures)
 *
 * This ensures zero-downtime deployments and graceful handling of backend issues.
 */
@Readiness
@ApplicationScoped
public class KruizeApiHealthCheck implements HealthCheck {
    private static final Logger log = LoggerFactory.getLogger(KruizeApiHealthCheck.class);

    @Inject
    @RestClient
    KruizeApiClient apiClient;

    @Override
    public HealthCheckResponse call() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Check /health endpoint
            apiClient.getHealth();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            log.debug("Kruize API health check passed in {}ms", responseTime);
            
            return HealthCheckResponse.named("Kruize API")
                    .up()
                    .withData("responseTime", responseTime + "ms")
                    .build();
                    
        } catch (Exception e) {
            log.warn("Kruize API health check failed: {}", e.getMessage());
            
            // Extract meaningful error message
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("status code")) {
                // Extract just the status code part for clarity
                int statusIndex = errorMessage.indexOf("status code");
                if (statusIndex > 0) {
                    errorMessage = "Kruize API returned: " + errorMessage.substring(statusIndex);
                }
            }
            
            return HealthCheckResponse.named("Kruize API")
                    .down()
                    .withData("error", errorMessage)
                    .build();
        }
    }
}
