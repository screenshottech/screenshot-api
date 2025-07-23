# Job Retry System Documentation

## Overview

The Screenshot API implements a comprehensive job retry system that handles failed and stuck screenshot jobs through both automatic and manual retry mechanisms. The system follows Clean Architecture principles and implements robust error handling, race condition prevention, and comprehensive audit trails.

## Architecture Components

### Domain Layer

#### 1. RetryType Enum
```kotlin
enum class RetryType {
    AUTOMATIC,  // System-initiated retry
    MANUAL      // User-initiated retry
}
```

#### 2. ScreenshotJob Entity Enhancements
```kotlin
data class ScreenshotJob(
    // ... existing fields ...
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val nextRetryAt: Instant? = null,
    val lastFailureReason: String? = null,
    val isRetryable: Boolean = true,
    val retryType: RetryType = RetryType.AUTOMATIC,
    val lockedBy: String? = null,
    val lockedAt: Instant? = null
)
```

**Key Methods:**
- `canRetry()`: Validates if job can be retried based on retryCount and maxRetries
- `isStuck()`: Detects jobs stuck in PROCESSING state >30 minutes
- `isLocked()`: Prevents race conditions with optimistic locking
- `scheduleRetry()`: Prepares job for automatic retry with exponential backoff
- `resetForManualRetry()`: Resets job state for manual retry
- `lock(workerId)` / `unlock()`: Manages job locking

#### 3. RetryPolicy Interface
```kotlin
interface RetryPolicy {
    fun shouldRetry(error: Exception): Boolean
    fun calculateDelay(attemptNumber: Int): Duration
    fun getMaxRetries(): Int
}
```

**DefaultRetryPolicyImpl Logic:**
- **Retryable Errors**: Network timeouts, connection failures, I/O errors
- **Non-retryable Errors**: Invalid arguments, security exceptions, insufficient credits
- **Backoff Strategy**: Exponential (5s → 25s → 125s)

### Application Layer

#### 1. ManualRetryScreenshotUseCase
**Purpose**: Handles user-initiated retries from dashboard/API

**Flow:**
1. Validates user authorization for the job
2. Checks job status (must be FAILED or stuck)
3. **NEW**: Validates if job is scheduled for automatic retry
4. **NEW**: Attempts to cancel scheduled automatic retry
5. Validates user has sufficient credits
6. Locks job to prevent race conditions
7. Resets job state and enqueues for immediate processing
8. Logs manual retry action

**Race Condition Prevention:**
```kotlin
// Check for scheduled automatic retry
if (job.nextRetryAt != null && job.nextRetryAt > Clock.System.now()) {
    val cancelled = queueRepository.cancelDelayedJob(job.id)
    if (!cancelled) {
        throw ValidationException.InvalidState("job", "scheduled for automatic retry", "available for manual retry")
    }
}
```

#### 2. ProcessStuckJobsUseCase
**Purpose**: Background recovery of jobs stuck in PROCESSING state

**Flow:**
1. Finds jobs in PROCESSING state older than 30 minutes
2. Locks each job to prevent concurrent processing
3. Evaluates if job should be retried using RetryPolicy
4. Schedules retry with appropriate delay or marks as failed
5. Logs stuck job recovery actions

#### 3. ProcessFailedRetryableJobsUseCase
**Purpose**: Background processing of failed jobs ready for retry

**Flow:**
1. Finds FAILED jobs that are retryable and within retry limits
2. Locks jobs to prevent race conditions
3. Uses RetryPolicy to determine if error type is retryable
4. Schedules retry or marks as permanently failed
5. Logs retry attempts

### Infrastructure Layer

#### 1. Enhanced QueueRepository
```kotlin
interface QueueRepository {
    // Existing methods...
    suspend fun enqueueDelayed(job: ScreenshotJob, delay: Duration)
    suspend fun dequeueReadyRetries(): List<ScreenshotJob>
    suspend fun requeueFailedJob(job: ScreenshotJob): Boolean
    suspend fun cancelDelayedJob(jobId: String): Boolean  // NEW
}
```

#### 2. RedisQueueAdapter Implementation
**Delayed Queue Strategy**: Uses Redis Sorted Set with execution timestamp as score

```kotlin
// Enqueue with delay
connection.sync().zadd(delayedQueueKey, executeAt.toDouble(), jobJson)

// Cancel delayed job
val allDelayedJobs = connection.sync().zrange(delayedQueueKey, 0, -1)
// Scan for jobId and remove from sorted set
```

#### 3. ScreenshotRepository Enhancements
```kotlin
interface ScreenshotRepository {
    // Existing methods...
    suspend fun tryLockJob(jobId: String, workerId: String): ScreenshotJob?
    suspend fun findStuckJobs(stuckAfterMinutes: Int = 30, limit: Int = 100): List<ScreenshotJob>
    suspend fun findFailedRetryableJobs(limit: Int = 100): List<ScreenshotJob>
    suspend fun findJobsReadyForRetry(limit: Int = 100): List<ScreenshotJob>
}
```

### Worker System

#### 1. JobRetryScheduler
**Purpose**: Background scheduler for automatic retry processing

**Scheduled Tasks:**
- **Stuck Jobs**: Every 5 minutes - recovers stuck jobs
- **Failed Retryable Jobs**: Every 30 seconds - processes failed jobs
- **Delayed Jobs**: Every 10 seconds - moves ready jobs to main queue

```kotlin
private suspend fun runScheduledTasks() {
    while (isRunning.get()) {
        val now = Clock.System.now()
        
        if (now - lastStuckJobsCheck >= stuckJobsInterval) {
            processStuckJobs()
        }
        
        if (now - lastRetryJobsCheck >= retryJobsInterval) {
            processRetryableJobs()
        }
        
        if (now - lastDelayedJobsCheck >= delayedJobsInterval) {
            processDelayedJobs()
        }
    }
}
```

#### 2. Enhanced ScreenshotWorker
**Retry Integration:**
```kotlin
private suspend fun handleJobFailure(job: ScreenshotJob, error: Exception, processingTime: Long) {
    val shouldRetry = job.canRetry() && retryPolicy.shouldRetry(error)
    
    if (shouldRetry) {
        val delay = retryPolicy.calculateDelay(job.retryCount)
        val retryJob = job.scheduleRetry(errorMessage, delay)
        
        screenshotRepository.update(retryJob)
        queueRepository.enqueueDelayed(retryJob, delay)
        
        // Log automatic retry
    } else {
        // Mark as permanently failed
    }
}
```

## User Interaction Flows

### Scenario 1: Automatic Retry (Worker Failure)

```
1. User submits screenshot request
2. Worker processes job but encounters network timeout
3. RetryPolicy.shouldRetry(SocketTimeoutException) → true
4. Job scheduled for retry in 5 seconds
5. JobRetryScheduler moves job back to main queue after delay
6. Worker reprocesses successfully
```

### Scenario 2: Manual Retry from Dashboard

```
1. User sees failed screenshot in dashboard
2. User clicks "Retry" button
3. Frontend: POST /api/v1/screenshots/{id}/retry
4. ManualRetryScreenshotUseCase validates and processes
5. Job immediately enqueued for processing
6. Worker processes job
```

### Scenario 3: Stuck Job Recovery

```
1. Job stuck in PROCESSING for >30 minutes
2. JobRetryScheduler detects via ProcessStuckJobsUseCase
3. Job automatically marked as failed and scheduled for retry
4. Retry processed by worker
```

### Scenario 4: Race Condition Handling

```
1. Job scheduled for automatic retry in 25 seconds
2. User attempts manual retry at 15 seconds
3. ManualRetryScreenshotUseCase cancels delayed job
4. Manual retry proceeds immediately
5. No duplicate processing occurs
```

## Error Handling & Edge Cases

### Race Condition Prevention

#### 1. Optimistic Locking
```kotlin
val lockedJob = screenshotRepository.tryLockJob(job.id, "manual-retry-user123")
    ?: throw ConcurrentModificationException("Job currently being processed")
```

#### 2. Delayed Job Cancellation
```kotlin
// Before manual retry
if (job.nextRetryAt != null && job.nextRetryAt > Clock.System.now()) {
    val cancelled = queueRepository.cancelDelayedJob(job.id)
    if (!cancelled) {
        throw ValidationException.InvalidState("job", "scheduled for automatic retry", "available for manual retry")
    }
}
```

#### 3. Status Validation
```kotlin
if (job.status != ScreenshotStatus.FAILED && !job.isStuck()) {
    throw ValidationException.InvalidState("job", "current status", "FAILED or stuck")
}
```

### Error Classification

| Error Type | Retryable | Strategy |
|------------|-----------|----------|
| SocketTimeoutException | ✅ Yes | Exponential backoff |
| ConnectException | ✅ Yes | Exponential backoff |
| IOException | ✅ Yes | Exponential backoff |
| IllegalArgumentException | ❌ No | Mark as failed |
| SecurityException | ❌ No | Mark as failed |
| IllegalStateException | ❌ No | Mark as failed |

### Credit Management

#### Manual Retry Credit Validation
```kotlin
val hasCredits = checkCreditsUseCase(CheckCreditsRequest(
    userId = request.userId,
    requiredCredits = job.jobType.defaultCredits
))

if (!hasCredits.hasEnoughCredits) {
    throw InsufficientCreditsException(...)
}
```

**Note**: Automatic retries do not require additional credit validation as the original job already reserved credits.

## Database Schema Changes

### Screenshots Table Additions
```sql
ALTER TABLE screenshots ADD COLUMN retry_count integer default 0 not null;
ALTER TABLE screenshots ADD COLUMN max_retries integer default 3 not null;
ALTER TABLE screenshots ADD COLUMN next_retry_at timestamp with time zone;
ALTER TABLE screenshots ADD COLUMN last_failure_reason text;
ALTER TABLE screenshots ADD COLUMN is_retryable boolean default true not null;
ALTER TABLE screenshots ADD COLUMN retry_type varchar(20) default 'AUTOMATIC' not null;
ALTER TABLE screenshots ADD COLUMN locked_by varchar(255);
ALTER TABLE screenshots ADD COLUMN locked_at timestamp with time zone;
```

### Performance Indexes
```sql
CREATE INDEX IF NOT EXISTS "idx_screenshots_retry_ready" 
ON screenshots(next_retry_at) 
WHERE status = 'QUEUED' AND is_retryable = true AND next_retry_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS "idx_screenshots_stuck_jobs" 
ON screenshots(status, updated_at) 
WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS "idx_screenshots_failed_retryable" 
ON screenshots(status, is_retryable, retry_count, max_retries) 
WHERE status = 'FAILED' AND is_retryable = true;

CREATE INDEX IF NOT EXISTS "idx_screenshots_locked_jobs" 
ON screenshots(locked_by, locked_at) 
WHERE locked_by IS NOT NULL;
```

## API Endpoints

### Manual Retry Endpoint
```kotlin
POST /api/v1/screenshots/{jobId}/retry

Request:
{
    "requestedBy": "user123" // or "api_key_abc"
}

Response:
{
    "jobId": "job_1234567890",
    "message": "Job queued for manual retry",
    "queuePosition": 5
}
```

### Error Responses
```kotlin
// Job being processed
409 Conflict: {
    "error": "CONCURRENT_MODIFICATION",
    "message": "Job is currently being processed by another worker"
}

// Insufficient credits
402 Payment Required: {
    "error": "INSUFFICIENT_CREDITS", 
    "requiredCredits": 1,
    "availableCredits": 0
}

// Invalid state
400 Bad Request: {
    "error": "VALIDATION_ERROR",
    "message": "Job must be in FAILED status or stuck (>30 min processing)"
}
```

## Monitoring & Observability

### Metrics Tracked
- Jobs retried (automatic vs manual)
- Retry success rates by attempt number
- Stuck job recovery counts
- Failed jobs marked as non-retryable
- Queue sizes (main vs delayed)

### Log Events
```kotlin
// Automatic retry scheduled
logger.info("Job failed but scheduled for retry: jobId={}, retryCount={}, delay={}s", 
    job.id, job.retryCount, delay.inWholeSeconds)

// Manual retry requested
logger.info("Manual retry requested for job: {} by user: {}", jobId, userId)

// Stuck job detected
logger.warn("Found {} stuck jobs", stuckJobs.size)

// Delayed job cancelled
logger.info("Cancelled scheduled automatic retry for manual retry: jobId={}", job.id)
```

### Health Checks
- JobRetryScheduler running status
- Queue depth monitoring
- Stuck job count alerts
- Failed retry rate alerts

## Configuration

### Retry Policy Settings
```kotlin
class DefaultRetryPolicyImpl : RetryPolicy {
    override fun getMaxRetries(): Int = 3
    override fun calculateDelay(attemptNumber: Int): Duration {
        return 5.seconds * (5 * attemptNumber) // 5s, 25s, 125s
    }
}
```

### Scheduler Intervals
```kotlin
class JobRetryScheduler(
    private val stuckJobsInterval: Duration = 5.minutes,     // Check stuck jobs
    private val retryJobsInterval: Duration = 30.seconds,    // Check failed jobs  
    private val delayedJobsInterval: Duration = 10.seconds   // Check delayed queue
)
```

### Database Limits
```kotlin
interface ScreenshotRepository {
    suspend fun findStuckJobs(stuckAfterMinutes: Int = 30, limit: Int = 100)
    suspend fun findFailedRetryableJobs(limit: Int = 100)
    suspend fun findJobsReadyForRetry(limit: Int = 100)
}
```

## Testing Strategy

### Unit Tests
- RetryPolicy error classification
- ScreenshotJob state transitions
- Use case validation logic
- Queue operations

### Integration Tests
- End-to-end retry flows
- Race condition scenarios
- Database consistency
- Redis queue operations

### Load Tests
- High retry volume handling
- Scheduler performance under load
- Queue depth management
- Worker scaling behavior

## Future Enhancements

### Phase 1: Operational Control (Short-term)
1. **Dynamic Scheduler Control**
   - REST API endpoints to pause/resume scheduler without restart
   - Granular control per scheduler type (stuck jobs, failed jobs, delayed jobs)
   - Temporary pause with auto-resume after specified duration
   ```kotlin
   POST /api/v1/admin/retry-scheduler/pause
   {
     "duration": "30m",
     "types": ["STUCK_JOBS", "FAILED_JOBS"]
   }
   ```

2. **Advanced Configuration**
   - Per-job-type retry policies
   - User-specific retry limits
   - Time-based retry windows (e.g., no retries during business hours)
   ```yaml
   retry:
     policies:
       - jobType: SCREENSHOT
         maxRetries: 5
         backoffStrategy: EXPONENTIAL
       - jobType: PDF_GENERATION
         maxRetries: 2
         backoffStrategy: FIXED
   ```

### Phase 2: Intelligent Retry System (Medium-term)
1. **Smart Error Classification**
   - ML-based error categorization
   - Historical success rate analysis
   - Dynamic retry decision based on patterns
   ```kotlin
   class SmartRetryPolicy(
     private val errorClassifier: MLErrorClassifier,
     private val historyAnalyzer: RetryHistoryAnalyzer
   ) : RetryPolicy {
     override fun shouldRetry(error: Exception): Boolean {
       val errorCategory = errorClassifier.classify(error)
       val successProbability = historyAnalyzer.predictSuccess(errorCategory)
       return successProbability > RETRY_THRESHOLD
     }
   }
   ```

2. **Circuit Breaker Integration**
   - Automatic retry suspension for failing services
   - Health check integration
   - Gradual retry resumption
   ```kotlin
   class CircuitBreakerRetryPolicy : RetryPolicy {
     private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
     
     override fun shouldRetry(error: Exception): Boolean {
       val service = extractService(error)
       val breaker = circuitBreakers.computeIfAbsent(service) { 
         CircuitBreaker(service)
       }
       return breaker.allowRequest()
     }
   }
   ```

3. **Retry Analytics Dashboard**
   - Real-time retry metrics
   - Success rate trends
   - Cost analysis (credits consumed by retries)
   - Alert configuration for retry anomalies

### Phase 3: Advanced Features (Long-term)
1. **Distributed Retry Coordination**
   - Multi-region retry handling
   - Cross-datacenter job migration
   - Global retry state management
   ```kotlin
   interface DistributedRetryCoordinator {
     suspend fun acquireRetrySlot(region: String, jobId: String): RetrySlot?
     suspend fun migrateJob(jobId: String, fromRegion: String, toRegion: String)
     suspend fun getGlobalRetryState(jobId: String): GlobalRetryState
   }
   ```

2. **Retry Budget System**
   - Per-user retry quotas
   - Organization-level retry limits
   - Cost-based retry decisions
   ```kotlin
   data class RetryBudget(
     val userId: String,
     val dailyRetryLimit: Int,
     val monthlyRetryLimit: Int,
     val costLimit: BigDecimal,
     val priorityLevel: Priority
   )
   ```

3. **Event-Driven Retry Architecture**
   - Complete event sourcing for job lifecycle
   - Retry event stream for external consumers
   - Webhook-based retry triggers
   ```kotlin
   sealed class RetryEvent {
     data class RetryScheduled(val jobId: String, val delay: Duration) : RetryEvent()
     data class RetryStarted(val jobId: String, val attempt: Int) : RetryEvent()
     data class RetrySucceeded(val jobId: String, val duration: Duration) : RetryEvent()
     data class RetryFailed(val jobId: String, val error: String) : RetryEvent()
   }
   ```

4. **Advanced Queue Management**
   - Priority-based retry queues
   - Fair queuing algorithms
   - Dynamic queue sizing based on load
   ```kotlin
   class PriorityRetryQueue {
     private val queues = mapOf(
       Priority.HIGH to DelayQueue<RetryJob>(),
       Priority.NORMAL to DelayQueue<RetryJob>(),
       Priority.LOW to DelayQueue<RetryJob>()
     )
   }
   ```

### Phase 4: Enterprise Features (Future)
1. **SLA-Based Retry Policies**
   - Customer-specific SLAs
   - Guaranteed retry times
   - Compensation for SLA breaches

2. **Retry Marketplace**
   - Custom retry strategies as plugins
   - Community-contributed error matchers
   - Retry strategy templates

3. **AI-Powered Optimization**
   - Predictive retry scheduling
   - Anomaly detection in retry patterns
   - Automated retry policy tuning

### Technical Considerations

**Current Architecture Strengths**
- Clean separation of concerns following hexagonal architecture
- Robust race condition prevention with optimistic locking
- Comprehensive audit trail and logging
- Flexible retry policy system

**Areas for Future Consideration**
- Scalability of optimistic locking under extreme load
- Redis sorted set performance with millions of delayed jobs
- Potential benefits of extracting retry logic to dedicated service
- Enhanced testing coverage for edge cases

**Performance Considerations**
- Current implementation handles moderate to high loads effectively
- May benefit from additional caching layers at scale
- Queue performance optimization opportunities exist

## Troubleshooting Guide

### Common Issues

#### 1. Jobs Stuck in Retry Loop
**Symptoms**: Same job retrying repeatedly without success
**Diagnosis**: Check `lastFailureReason` and retry count
**Solution**: Investigate underlying infrastructure issue

#### 2. Manual Retry Fails with "Already Scheduled"
**Symptoms**: User cannot retry job that appears failed
**Diagnosis**: Job has `nextRetryAt` in future
**Solution**: Wait for automatic retry or implement cancel + retry UX

#### 3. Delayed Jobs Not Processing
**Symptoms**: Jobs stuck in delayed queue
**Diagnosis**: JobRetryScheduler not running or Redis connection issues
**Solution**: Restart scheduler or check Redis connectivity

#### 4. Race Condition Errors
**Symptoms**: Frequent `ConcurrentModificationException`
**Diagnosis**: Multiple workers/users trying to modify same job
**Solution**: Review locking strategy and job state validation

### Debug Commands

```bash
# Check delayed queue in Redis
redis-cli ZRANGE screenshot_api:queue:delayed_jobs 0 -1 WITHSCORES

# Check stuck jobs
curl -X POST http://localhost:8080/api/v1/admin/jobs/stuck

# Check retry scheduler status  
curl http://localhost:8080/api/v1/admin/workers/status

# Trigger manual stuck job processing
curl -X POST http://localhost:8080/api/v1/admin/jobs/process-stuck
```

---

## Implementation Checklist

### ✅ Completed
- [x] Domain entities and enums
- [x] Use case implementations
- [x] Repository interfaces and implementations
- [x] Queue management with Redis
- [x] Worker integration
- [x] Background scheduler
- [x] API endpoints
- [x] Database migrations
- [x] Error handling and validation
- [x] Race condition prevention
- [x] Optimistic locking
- [x] Delayed job cancellation
- [x] Comprehensive logging

### 🚧 In Progress
- [ ] Comprehensive test coverage
- [ ] Performance optimization
- [ ] Monitoring dashboard

### 📋 Planned
- [ ] Advanced retry strategies
- [ ] Analytics and reporting
- [ ] Webhook notifications for retry events
- [ ] GraphQL API support
- [ ] SDK libraries for popular languages

---

*Last updated: June 17, 2025*
*Authors: Development Team*
*Version: 1.0*