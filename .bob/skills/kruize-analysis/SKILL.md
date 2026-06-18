---
name: kruize-analysis
description: Analyze Kubernetes workload optimization data from Kruize MCP server including cost optimization, performance tuning, idle workload detection, and experiment tracking
---

# Kruize Workload Analysis

Comprehensive workflow for analyzing Kubernetes resource optimization data from Kruize MCP server.

## Prerequisites

Review `kruize-reference.md` in this skill directory for foundational concepts about Kruize, cost vs performance optimization, confidence levels, and resource configuration.

## Available MCP Tools

- **listAllExperiments** - List all Kruize experiments
- **listAllRecommendations** - Get complete recommendation data for all the containers
- **getCostOptimizedRecommendations** - Get cost-optimized recommendations (by container name, optional namespace)
- **getPerformanceOptimizedRecommendations** - Get performance-optimized recommendations (by container name, optional namespace)
- **getIdleWorkloads** - Identify workloads with less than a millicore CPU usage (with optional detailed recommendations)

## Workflow 1: Cost Optimization Analysis

**Goal**: Identify and prioritize cost-saving opportunities

### Step 1: Fetch Cost Recommendations
```
Use: getCostOptimizedRecommendations
Parameters: containerName (required), namespace (optional)
```

### Step 2: Identify High-Value Opportunities

Focus on workloads with:
- Large gap between current and recommended resources
- High confidence levels (>0.8)
- Long monitoring duration
- Idle workload notifications (code 323001)

### Step 3: Calculate Savings Potential

For each workload:
- Compare current vs recommended CPU/memory
- Calculate percentage reduction
- Estimate monthly cost savings
- Aggregate total savings across workloads

### Step 4: Prioritize by Risk and Impact

**Immediate Action (High Priority):**
- High confidence (>0.8) + significant savings
- Idle workloads
- Non-critical workloads

**Planned Implementation (Medium Priority):**
- Medium confidence (0.5-0.8)
- Moderate savings
- Requires stakeholder review

**Monitor Further (Low Priority):**
- Low confidence (<0.5)
- Minimal savings
- Recently deployed

### Step 5: Generate Report

```
## Cost Optimization Report: [Container/Namespace]

### Current State
- CPU: [requests/limits]
- Memory: [requests/limits]
- Monitoring Duration: [hours/days]

### Recommendations by Term (60th Percentile CPU)
**Short-term (24 hours)**:
- CPU: [cores] - Confidence: [level] - Savings: [%]
- Memory: [recommended value]

**Medium-term (7 days)**:
- CPU: [cores] - Confidence: [level] - Savings: [%]
- Memory: [recommended value]

**Long-term (15 days)**:
- CPU: [cores] - Confidence: [level] - Savings: [%]
- Memory: [recommended value]

### Priority: [High/Medium/Low]

### Action Plan
1. [Specific implementation step]
2. [Validation and monitoring]
3. [Rollback plan if needed]
```

## Workflow 2: Idle Workload Detection

**Goal**: Identify and eliminate wasted resources

### Step 1: Detect Idle Workloads
```
Use: getIdleWorkloads
Parameters: includeRecommendations (true for detailed analysis, false for summary)
```

**Understanding Idle Workloads:**
- Defined as containers with CPU usage **< 1 millicore (0.001 cores)** in the observed term
- **No CPU recommendation** can be generated for idle containers
- Only **memory recommendations** are provided for idle workloads
- These represent the highest cost-saving opportunities

### Step 2: Categorize by Severity

**Critical (Immediate Action):**
- Long monitoring duration (>7 days)
- High confidence (>0.8)
- Consistently idle across all terms
- CPU usage < 1 millicore throughout observation period

**Medium (Review Required):**
- Moderate duration (3-7 days)
- Medium confidence (0.5-0.8)
- May have periodic usage
- Intermittent idle periods

**Low (Continue Monitoring):**
- Short duration (<3 days)
- Low confidence (<0.5)
- Recently deployed
- Insufficient data for confident assessment

### Step 3: Determine Actions

**For Critical Idle Workloads:**
- Decommission if no business justification
- Scale to zero with event-driven scaling (HPA/KEDA)
- Consolidate with similar workloads
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

### Step 4: Calculate Impact

```
## Idle Workload Report

### Summary
- Total Idle Workloads: [count]

### Critical Priority ([count] workloads)
[List with namespace, container, duration, confidence]

### Key Indicators
- CPU Recommendation: Not available (< 1 millicore usage)
- Memory Recommendation: Available (based on observed usage + buffer)

### Recommended Actions
1. [Decommission list with justification]
2. [Scale-to-zero candidates]
3. [Further investigation needed]

### Risk Mitigation
- [Backup/rollback plans]
- [Stakeholder communication]
- [Validation of business requirements]
```

## Workflow 3: Performance Optimization Analysis

**Goal**: Ensure adequate resources for performance-critical workloads

### Step 1: Fetch Performance Recommendations
```
Use: getPerformanceOptimizedRecommendations
Parameters: containerName (required), namespace (optional)
```

### Step 2: Identify Under-Provisioned Workloads

Look for:
- Current resources below recommended levels
- High confidence recommendations
- Performance-critical applications
- SLA-sensitive workloads

### Step 3: Assess Performance Risk

**High Risk (Immediate Action):**
- Significant under-provisioning
- High confidence recommendations
- Production workloads with SLAs

**Medium Risk (Planned Upgrade):**
- Moderate under-provisioning
- Medium confidence
- Non-critical but important workloads

**Low Risk (Monitor):**
- Minimal under-provisioning
- Low confidence
- Development/test environments

### Step 4: Generate Performance Report

```
## Performance Optimization: [Container/Namespace]

### Current Allocation
- CPU: [requests/limits]
- Memory: [requests/limits]

### Performance Recommendations (98th Percentile CPU)
**Short-term (24 hours)**:
- CPU: [cores] - Uses 98th percentile - Confidence: [level]
- Memory: [recommended value]

**Medium-term (7 days)**:
- CPU: [cores] - Uses 98th percentile - Confidence: [level]
- Memory: [recommended value]

**Long-term (15 days)**:
- CPU: [cores] - Uses 98th percentile - Confidence: [level]
- Memory: [recommended value]

### Risk Assessment: [High/Medium/Low]

### Implementation Plan
1. [Resource increase steps]
2. [Performance validation]
3. [Monitoring strategy]
```

## Workflow 4: Balanced Cost vs Performance Analysis

**Goal**: Find optimal balance between cost and performance

### Step 1: Fetch Both Recommendations
```
Use: getCostOptimizedRecommendations AND getPerformanceOptimizedRecommendations
For same container/namespace
```

### Step 2: Compare Recommendations

Analyze the gap between:
- Cost-optimized resources (minimum safe allocation)
- Performance-optimized resources (maximum performance)
- Current allocation

### Step 3: Determine Optimal Strategy

**Cost-Focused Workloads:**
- Non-critical applications
- Development/test environments
- Batch processing jobs
- Use cost recommendations

**Performance-Focused Workloads:**
- User-facing applications
- Real-time processing
- SLA-critical services
- Use performance recommendations

**Balanced Approach:**
- Important but cost-conscious workloads
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
- Savings: [%]
- Confidence: [level]
- Risk: [assessment]
- Example: 93% CPU reduction, 53% memory reduction possible

### Performance-Optimized Option (98th Percentile CPU)
- CPU: [cores] - Uses 98th percentile for reliability
- Memory: [recommended value]
- Additional Cost: [%]
- Confidence: [level]
- Benefit: [assessment]
- Example: 41% CPU reduction, 53% memory reduction while maintaining performance

### Key Differences
- **CPU Strategy**: Cost uses 60th percentile vs Performance uses 98th percentile
- **Memory Strategy**: Both use same formula (prevents OOM scenarios)
- **Request/Limit**: Both set to same value for consistency

### Recommendation: [Cost/Performance/Balanced]
Justification: [Why this option is best for this workload]

### Implementation Plan
[Specific steps based on chosen strategy]
```

## Workflow 5: Experiment Tracking and Review

**Goal**: Monitor optimization experiments and track results

### Step 1: List All Experiments
```
Use: listAllExperiments
Returns: All active and completed experiments
```

### Step 2: Analyze Experiment Status

Review each experiment:
- Experiment name and type
- Current status
- Monitoring duration
- Target cluster

### Step 3: Identify Actionable Experiments

**Ready for Action:**
- Completed experiments with high confidence
- Long-running experiments with stable data
- Experiments showing clear optimization opportunities

**Needs More Time:**
- Recently started experiments
- Low confidence scores
- Insufficient monitoring duration

### Step 4: Generate Experiment Summary

```
## Kruize Experiment Summary

### Active Experiments: [count]
### Completed Experiments: [count]

### Ready for Recommendation Analysis ([count])
[List experiments with sufficient monitoring duration for separate recommendation lookup]

### Monitoring in Progress ([count])
[List experiments needing more data]

### Recommended Next Steps
1. [Fetch recommendations separately for relevant experiments]
2. [Extend monitoring for experiments with insufficient data]
3. [Review and archive old experiments]
```

## Best Practices

### General Guidelines
- Always check confidence levels before implementing changes
- Start with highest confidence, highest impact workloads
- Implement changes incrementally with monitoring
- Document all optimization decisions and rationale
- Communicate with application owners before changes
- Plan rollback procedures for production workloads

### Cost Optimization
- Prioritize idle workloads first (highest ROI)
- Use long-term recommendations for production
- Include safety margins for resource limits
- Track actual vs projected savings

### Performance Optimization
- Never compromise SLAs for cost savings
- Implement during maintenance windows
- Monitor application metrics after changes
- Consider peak load requirements

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
- Update confidence thresholds based on experience

## Special Considerations

### Notification 323001 (Idle Workloads)
- Highest priority for cost savings
- **Definition**: CPU usage < 1 millicore (0.001 cores) in observed term
- **Warning Icon**: Displayed in Kruize UI for idle containers
- **CPU Recommendation**: Cannot be generated for idle workloads
- **Memory Recommendation**: Still provided using standard formula
- Consider decommissioning or scaling to zero
- Validate business justification before action
- Review box plots to confirm consistent idle state

### Low Confidence Recommendations
- Extend monitoring period (aim for 15-day long-term data)
- Implement cautiously with close monitoring
- Start with short-term recommendations (24 hours)
- Validate with application performance data
- Review box plots for usage patterns and variations

### Seasonal Workloads
- Consider usage patterns over time
- May appear idle during off-season
- Review historical data before decommissioning
- Implement auto-scaling instead of static sizing

### Multi-Cluster Environments
- Use namespace filtering for targeted analysis
- Compare recommendations across clusters
- Identify cluster-specific optimization opportunities
- Standardize resource allocation policies