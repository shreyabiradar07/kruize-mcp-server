# Kruize Reference Guide

## What is Kruize?

Kruize is a Kubernetes resource optimization engine that analyzes workload performance and provides intelligent recommendations for right-sizing CPU and memory resources. It helps reduce cloud costs while maintaining application performance by providing container right-sizing recommendations in the form of CPU and memory requests and limits.

## How Kruize Works

Kruize provides container right-sizing recommendations based on resource usage patterns:
- Recommendations are based on resource usage over **24 hours (short term)**, **7 days (medium term)**, and **15 days (long term)**
- Provides both **cost-optimized** and **performance-optimized** suggestions for each term on a per-container basis
- Request and limit values for both CPU and memory are set to be the same for consistency

## Key Concepts

### Experiments
Kruize monitors Kubernetes workloads through "experiments" that collect metrics over time. Each experiment tracks:
- Container resource usage (CPU, memory)
- Performance characteristics
- Cost implications
- Optimization opportunities

### Recommendation Engines

Kruize provides two types of optimization recommendations:

#### Cost Engine
- **Goal**: Minimize resource costs
- **Strategy**: Uses the **60th percentile** for CPU usage (including throttling) for the given term
- **Use Case**: Cost-sensitive workloads where slight performance trade-offs are acceptable
- **Output**: Recommendations that prioritize savings

#### Performance Engine
- **Goal**: Maximize application performance
- **Strategy**: Uses the **98th percentile** for CPU usage (including any throttling) for the given term
- **Use Case**: Performance-critical workloads where responsiveness matters most
- **Output**: Recommendations that prioritize speed and reliability

### Recommendation Terms

Kruize provides recommendations across three time horizons:

- **Short-term**: Based on 24 hours of historical data - Quick wins for immediate adjustments
- **Medium-term**: Based on 7 days of historical data - Balanced approach with better reliability
- **Long-term**: Based on 15 days of historical data - Most reliable recommendations

Longer monitoring periods generally provide higher confidence recommendations.

### Confidence Levels

Each recommendation includes a confidence score (0.0 to 1.0):
- **0.8-1.0**: High confidence - Safe to implement
- **0.5-0.8**: Medium confidence - Review carefully
- **0.0-0.5**: Low confidence - Needs more monitoring data

### Idle Workloads

Special category identified by notification code **323001**:
- **Definition**: Containers with CPU usage **< 1 millicore (0.001 cores)** in the observed term
- Severely underutilized resources
- Prime candidates for decommissioning or scaling to zero
- Highest cost-saving potential
- **No CPU Recommendation**: Kruize cannot generate CPU recommendations for idle containers (only memory recommendations are provided)

### Resource Configuration

Kruize recommendations include:
- **Requests**: Minimum guaranteed resources (affects scheduling)
- **Limits**: Maximum allowed resources (affects throttling)
- **Unified Values**: Request and limit values for both CPU and memory are set to be the same
- **Variation**: Expected fluctuation range for capacity planning

### Warnings and Notifications

Kruize displays warnings in certain conditions:

1. **Idle Containers (Code 323001)**
   - Containers with < 1 millicore CPU usage
   - No CPU recommendation can be generated
   - Only memory recommendations provided

2. **Missing Request or Limit**
   - Warning when CPU/memory request or limit is not set in current configuration
   - Helps identify incomplete resource configurations

## When to Use Cost vs Performance Recommendations

**Use Cost Recommendations When:**
- Budget constraints are primary concern
- Workload can tolerate slight performance variations
- Over-provisioning is evident
- Non-critical applications (dev/test environments)

**Use Performance Recommendations When:**
- Application responsiveness is critical
- SLAs must be maintained
- User experience is priority
- Production workloads with strict requirements

**Use Both When:**
- Balancing cost and performance
- Comparing trade-offs
- Making informed decisions about resource allocation