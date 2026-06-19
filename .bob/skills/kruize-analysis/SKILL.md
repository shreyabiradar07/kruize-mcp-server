---
name: kruize-analysis
description: Analyze Kubernetes workload optimization data from Kruize MCP server including cost optimization, performance tuning, idle workload detection, and experiment tracking
---

# Kruize Workload Analysis

Comprehensive workflow for analyzing Kubernetes resource optimization data from Kruize MCP server.

## Prerequisites

Review [`kruize-reference.md`](.bob/skills/kruize-analysis/kruize-reference.md) in this skill directory for foundational concepts about Kruize, cost vs performance optimization, box plots data, resource configuration, and runtime/framework recommendations.

## Available MCP Tools

- **listAllExperiments** - List all Kruize experiments
- **listAllRecommendations** - Get complete recommendation data for all the containers
- **getCostOptimizedRecommendations** - Get cost-optimized recommendations (by container name, optional namespace)
- **getPerformanceOptimizedRecommendations** - Get performance-optimized recommendations (by container name, optional namespace)
- **getIdleWorkloads** - Identify workloads with less than a millicore CPU usage (with optional detailed recommendations)

## Workflow 1: Cost Optimization Analysis

**Goal**: Analyze cost-saving opportunities for a specific container

### Step 1: Fetch Cost Recommendations for Container

```
Use: getCostOptimizedRecommendations
Parameters:
  - containerName (required)
  - namespace (optional)
```

### Step 2: Analyze Container's Optimization Potential

Review the container's recommendation data for:
- Gap between current and recommended resources
- Monitoring duration confidence
- Idle workload notification (code 323001) if present
- Box plots showing usage patterns and consistency

### Step 3: Analyze Resource Reduction Potential

For the container:
- Compare current vs recommended CPU/memory
- Calculate percentage reduction
- Review box plots for usage patterns and variations
- Assess consistency of resource usage over time

### Step 4: Assess Risk and Impact

**Immediate Action (High Priority):**
- Long monitoring duration + significant resource reduction
- Idle container (< 1 millicore CPU usage)
- Non-critical container
- Box plots showing stable, consistent patterns

**Planned Implementation (Medium Priority):**
- Moderate monitoring duration
- Moderate resource reduction
- Requires stakeholder review
- Box plots showing some variation

**Monitor Further (Low Priority):**
- Short monitoring duration
- Minimal resource reduction
- Recently deployed container
- Box plots showing high variation or insufficient data

### Step 5: Generate Report

```
## Cost Optimization Report: [Container/Namespace]

### Current State
- CPU: [requests/limits]
- Memory: [requests/limits]
- Monitoring Duration: [hours/days]

### Recommendations by Term (60th Percentile CPU)
**Short-term (24 hours)**:
- CPU: [cores] - Reduction: [%]
- Memory: [recommended value]
- Box Plots: [min/max/median values]

**Medium-term (7 days)**:
- CPU: [cores] - Reduction: [%]
- Memory: [recommended value]
- Box Plots: [min/max/median values]

**Long-term (15 days)**:
- CPU: [cores] - Reduction: [%]
- Memory: [recommended value]
- Box Plots: [min/max/median values]

### Runtime Recommendations (if available)
**JVM Settings** (OpenJDK/Hotspot, IBM Semeru/OpenJ9):
- GCPolicy: [recommended value]
- MaxRAMPercentage: [recommended value]

**Framework Settings** (Quarkus):
- quarkus.thread-pool.core-threads: [recommended value]

*Note: Runtime recommendations appear automatically when application metrics are available via Prometheus/Thanos and proper labels are set (e.g., `com.redhat.component-name: "Quarkus"` for Quarkus apps)*

### Priority: [High/Medium/Low]

### Action Plan
1. [Specific implementation step]
2. [Validation and monitoring]
3. [Rollback plan if needed]
```

## Workflow 2: Idle Container Detection

**Goal**: Identify if a specific container is wasting resources

### Step 1: Check Container for Idle Status

**Option A - Check Specific Container:**
```
Use: getCostOptimizedRecommendations
Parameters: containerName (required), namespace (optional)
Look for: Notification code 323001 in response
```

**Option B - List All Idle Containers:**
```
Use: getIdleWorkloads
Parameters: includeRecommendations (true for detailed analysis, false for summary)
Then: Identify your target container in the results
```

**Understanding Idle Containers:**
- Defined as containers with CPU usage **< 1 millicore (0.001 cores)** in the observed term
- **No CPU recommendation** can be generated for idle containers
- Only **memory recommendations** are provided for idle containers
- These represent the highest cost-saving opportunities

### Step 2: Assess Severity

**Critical (Immediate Action):**
- Long monitoring duration (>7 days)
- Consistently idle across all terms
- CPU usage < 1 millicore throughout observation period
- Box plots confirming minimal CPU activity

**Medium (Review Required):**
- Moderate duration (3-7 days)
- May have periodic usage
- Intermittent idle periods
- Box plots showing occasional spikes

**Low (Continue Monitoring):**
- Short duration (<3 days)
- Recently deployed
- Insufficient data for assessment
- Box plots showing high variation or limited data points

### Step 3: Determine Action for Container

**For Critical Idle Container:**
- Decommission if no business justification
- Scale to zero with event-driven scaling (HPA/KEDA)
- Consolidate with similar containers
- Archive if data retention needed

**For Medium Priority:**
- Investigate usage patterns
- Implement auto-scaling
- Right-size to minimum viable
- Set monitoring alerts

**For Low Priority:**
- Extend monitoring period
- Document business purpose
- Schedule follow-up review

### Step 4: Generate Container Report

```
## Idle Container Report: [Container Name]

### Container Details
- Namespace: [namespace]
- Monitoring Duration: [hours/days]
- Severity: [Critical/Medium/Low]

### Key Indicators
- CPU Usage: < 1 millicore (idle)
- CPU Recommendation: Not available (< 1 millicore usage)
- Memory Recommendation: [value] (based on observed usage + buffer)
- Box Plots: [Show minimal CPU activity and memory usage patterns]

### Recommended Action
[Specific action: decommission/scale-to-zero/investigate/monitor]

### Risk Mitigation
- [Backup/rollback plan]
- [Stakeholder communication needed]
- [Business requirement validation]
```

## Workflow 3: Stability Performance Optimization Analysis
 Add guardrails to ignore rest of tools (no idle info to be exposed or considered)

**Goal**: Ensure adequate resources for a performance-critical container

### Step 1: Fetch Performance Recommendations for Container
```
Use: getPerformanceOptimizedRecommendations
Parameters:
  - containerName (required)
  - namespace (optional)
```

### Step 2: Check for Idle Container Status

**IMPORTANT**: Before proceeding with performance optimization, verify the container is not idle:
- Check response for notification code 323001 (idle workload indicator)
- If container has CPU usage < 1 millicore, it is idle
- **Idle containers cannot receive CPU recommendations** and should not be performance-optimized
- If idle, refer to Workflow 2 (Idle Container Detection) instead

### Step 3: Identify Under-Provisioning

Analyze the container for:
- Current resources below recommended levels
- Performance-critical application
- SLA-sensitive workload
- Box plots showing resource constraints or throttling

### Step 4: Assess Performance Risk

**High Risk (Immediate Action):**
- Significant under-provisioning
- Production container with SLAs
- Box plots showing frequent resource limits being hit

**Medium Risk (Planned Upgrade):**
- Moderate under-provisioning
- Non-critical but important container
- Box plots showing occasional resource constraints

**Low Risk (Monitor):**
- Minimal under-provisioning
- Development/test environment
- Box plots showing adequate headroom

### Step 5: Generate Performance Report

```
## Performance Optimization: [Container/Namespace]

### Idle Status Check
- Notification 323001: [Present/Not Present]
- Container Status: [Active/Idle]
- **Note**: If idle (CPU < 1 millicore), performance optimization is not applicable

### Current Allocation
- CPU: [requests/limits]
- Memory: [requests/limits]

### Performance Recommendations (98th Percentile CPU)
**Short-term (24 hours)**:
- CPU: [cores] - Uses 98th percentile
- Memory: [recommended value]
- Box Plots: [min/max/median values]

**Medium-term (7 days)**:
- CPU: [cores] - Uses 98th percentile
- Memory: [recommended value]
- Box Plots: [min/max/median values]

**Long-term (15 days)**:
- CPU: [cores] - Uses 98th percentile
- Memory: [recommended value]
- Box Plots: [min/max/median values]

### Runtime Recommendations (if available)
**JVM Settings** (OpenJDK/Hotspot, IBM Semeru/OpenJ9):
- GCPolicy: [recommended value]
- MaxRAMPercentage: [recommended value]

**Framework Settings** (Quarkus):
- quarkus.thread-pool.core-threads: [recommended value]

*Note: Runtime recommendations appear automatically when application metrics are available via Prometheus/Thanos and proper labels are set*

### Risk Assessment: [High/Medium/Low]

### Implementation Plan
1. [Resource increase steps]
2. [Performance validation]
3. [Monitoring strategy]
```

## Workflow 4: Balanced Cost vs Performance Analysis

**Goal**: Find optimal balance between cost and performance for a container

### Step 1: Fetch Both Recommendations for Container
```
Use: getCostOptimizedRecommendations
Parameters: containerName (required), namespace (optional)

AND

Use: getPerformanceOptimizedRecommendations
Parameters: containerName (required), namespace (optional)
```

### Step 2: Compare Recommendations

Analyze the gap between:
- Cost-optimized resources (minimum safe allocation)
- Performance-optimized resources (maximum performance)
- Current allocation

### Step 3: Determine Optimal Strategy for Container

**Cost-Focused Container:**
- Non-critical application
- Development/test environment
- Batch processing job
- Use cost recommendations

**Performance-Focused Container:**
- User-facing application
- Real-time processing
- SLA-critical service
- Use performance recommendations

**Balanced Approach:**
- Important but cost-conscious container
- Choose medium-term recommendations
- Consider variation ranges
- Implement with monitoring

### Step 4: Generate Comparison Report

```
## Cost vs Performance Analysis: [Container]

### Current State
- Resources: [CPU/Memory]

### Cost-Optimized Option (60th Percentile CPU)
- CPU: [cores] - Uses 60th percentile for cost savings
- Memory: [recommended value]
- Reduction: [%]
- Risk: [assessment]
- Box Plots: [usage patterns and variations]
- Example: 93% CPU reduction, 53% memory reduction possible

### Performance-Optimized Option (98th Percentile CPU)
- CPU: [cores] - Uses 98th percentile for reliability
- Memory: [recommended value]
- Increase: [%]
- Benefit: [assessment]
- Box Plots: [usage patterns and headroom]
- Example: 41% CPU reduction, 53% memory reduction while maintaining performance

### Runtime Recommendations (if available in either option)
**JVM Settings**:
- GCPolicy: [recommended value]
- MaxRAMPercentage: [recommended value]

**Framework Settings** (Quarkus):
- quarkus.thread-pool.core-threads: [recommended value]

*Note: Runtime recommendations are included automatically when prerequisites are met (application metrics via Prometheus/Thanos, proper labels)*

### Key Differences
- **CPU Strategy**: Cost uses 60th percentile vs Performance uses 98th percentile
- **Memory Strategy**: Both use same formula (prevents OOM scenarios)
- **Request/Limit**: Both set to same value for consistency
- **Runtime Settings**: Same recommendations for both cost and performance options

### Recommendation: [Cost/Performance/Balanced]
Justification: [Why this option is best for this workload]

### Implementation Plan
[Specific steps based on chosen strategy]
```

## Workflow 5: Container Experiment Tracking

**Goal**: Monitor optimization experiment for a specific container

### Step 1: List All Experiments
```
Use: listAllExperiments
Returns: All active and completed experiments
Then: Identify your target container's experiment
```

### Step 2: Analyze Container's Experiment Status

Review the experiment:
- Experiment name and type
- Current status
- Monitoring duration
- Target cluster

### Step 3: Determine if Container is Ready for Optimization

**Ready for Action:**
- Completed experiment with sufficient monitoring duration
- Long-running experiment with stable data
- Experiment showing clear optimization opportunities
- Box plots indicating consistent usage patterns

**Needs More Time:**
- Recently started experiment
- Insufficient monitoring duration
- Box plots showing high variation or limited data

### Step 4: Generate Container Experiment Report

```
## Container Experiment Report: [Container Name]

### Experiment Details
- Experiment Name: [name]
- Status: [active/completed]
- Monitoring Duration: [hours/days]
- Target Cluster: [cluster name]

### Readiness Assessment
[Ready for optimization / Needs more monitoring time]

### Recommended Next Steps
1. [Fetch cost/performance recommendations for this container]
2. [Extend monitoring if insufficient data]
3. [Implement recommendations if ready]
```

## Best Practices

### General Guidelines
- Review box plots to understand usage patterns before implementing changes
- Start with workloads showing stable patterns and significant resource reduction potential
- Implement changes incrementally with monitoring
- Document all optimization decisions and rationale
- Communicate with application owners before changes
- Plan rollback procedures for production workloads

### Cost Optimization
- Prioritize idle workloads first (highest resource reduction potential)
- Use long-term recommendations for production
- Include safety margins for resource limits
- Review box plots to validate consistent low usage patterns
- Check for runtime recommendations (JVM/framework settings) alongside CPU/memory optimizations

### Performance Optimization
- Never compromise SLAs for cost savings
- Implement during maintenance windows
- Monitor application metrics after changes
- Consider peak load requirements
- Apply runtime recommendations (GC policies, thread pools) to complement resource increases

### Risk Management
- Test in non-production first
- Implement during low-traffic periods
- Have rollback plans ready
- Monitor closely after implementation
- Validate with application teams

### Continuous Improvement
- Review optimizations regularly (monthly/quarterly)
- Track optimization success rates
- Adjust strategies based on results
- Share learnings across teams
- Analyze box plots trends over time to refine recommendations

## Special Considerations

### Notification 323001 (Idle Container)
- Highest priority for cost savings
- **Definition**: CPU usage < 1 millicore (0.001 cores) in observed term
- **Warning Icon**: Displayed in Kruize UI for idle containers
- **CPU Recommendation**: Cannot be generated for idle containers
- **Memory Recommendation**: Still provided using standard formula
- Consider decommissioning or scaling to zero
- Validate business justification before action
- Review box plots to confirm consistent idle state

### Container with High Variation
- Extend monitoring period (aim for 15-day long-term data)
- Implement cautiously with close monitoring
- Start with short-term recommendations (24 hours)
- Validate with application performance data
- Review box plots for usage patterns and variations to understand container behavior

### Seasonal Container
- Consider usage patterns over time
- May appear idle during off-season
- Review historical data before decommissioning
- Implement auto-scaling instead of static sizing

### Runtime and Framework Recommendations
- **Automatic inclusion**: Runtime recommendations appear in the same response as CPU/memory recommendations when prerequisites are met
- **Prerequisites**: Application metrics via Prometheus/Thanos, proper metric exposure, and labels (e.g., `com.redhat.component-name: "Quarkus"`)
- **Supported stacks**: OpenJDK/Hotspot, IBM Semeru/OpenJ9 (JVM), Quarkus (framework)
- **Container experiments only**: Runtime recommendations are not available for namespace-level experiments
- **Implementation order**: Apply CPU/memory changes first, then runtime configurations
- **Key tunables**: GCPolicy, MaxRAMPercentage (JVM), quarkus.thread-pool.core-threads (Quarkus)
- **Validation**: Monitor GC behavior, heap utilization, and thread pool saturation after applying runtime recommendations

### Container in Multi-Cluster Environments
- Use namespace filtering for targeted container analysis
- Compare container's recommendations across clusters
- Identify cluster-specific optimization opportunities for the container
- Standardize resource allocation policies for similar containers