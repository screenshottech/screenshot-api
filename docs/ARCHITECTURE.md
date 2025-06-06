# 🏗️ Architecture Overview

## 📋 Table of Contents

- [Architecture Philosophy](#-architecture-philosophy)
- [Clean Architecture Layers](#-clean-architecture-layers)
- [Domain Layer](#-domain-layer)
- [Application Layer](#-application-layer)
- [Infrastructure Layer](#-infrastructure-layer)
- [Data Flow](#-data-flow)
- [Design Patterns](#-design-patterns)
- [Technology Stack](#-technology-stack)
- [Scalability Strategy](#-scalability-strategy)
- [Security Architecture](#-security-architecture)

## 🎯 Architecture Philosophy

This Screenshot API is built using **Clean Architecture** (also known as Hexagonal Architecture) with **Domain-Driven Design** principles. The goal is to create a system that is:

- **Independent of Frameworks**: Business logic doesn't depend on external libraries
- **Testable**: Business rules can be tested without UI, databases, or external services
- **Independent of UI**: Multiple interfaces can be plugged in (REST API, GraphQL, CLI)
- **Independent of Database**: Can switch between PostgreSQL, MongoDB, or in-memory storage
- **Independent of External Services**: Business rules don't know about external integrations

## 🔄 Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                Application Layer                    │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │              Domain Layer                   │   │   │
│  │  │                                             │   │   │
│  │  │  • Entities (User, Screenshot, ApiKey)     │   │   │
│  │  │  • Value Objects (ScreenshotRequest)       │   │   │
│  │  │  • Domain Services (RateLimitingService)   │   │   │
│  │  │  • Repository Interfaces                   │   │   │
│  │  │  • Domain Exceptions                       │   │   │
│  │  │                                             │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  │                                                     │   │
│  │  • Use Cases (TakeScreenshotUseCase)               │   │
│  │  • Input/Output Ports                              │   │
│  │  • Application Services                            │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  • REST Controllers                                        │
│  • Database Adapters (PostgreSQL, InMemory)               │
│  • Cache Adapters (Redis, InMemory)                       │
│  • External Service Adapters (Playwright, Storage)        │
│  • Configuration & Dependency Injection                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 🎯 Domain Layer

The **core business logic** that is independent of any external concerns.

### Entities

```kotlin
// Core business entities
data class User(
    val id: String,
    val email: String,
    val name: String,
    val creditsRemaining: Int,
    val planId: String,
    val status: UserStatus,
    val createdAt: Instant
) {
    fun hasCredits(amount: Int): Boolean = creditsRemaining >= amount
    fun deductCredits(amount: Int): User = copy(creditsRemaining = creditsRemaining - amount)
}

data class ScreenshotJob(
    val id: String,
    val userId: String,
    val request: ScreenshotRequest,
    val status: ScreenshotStatus,
    val resultUrl: String? = null,
    val createdAt: Instant
) {
    fun markAsProcessing(): ScreenshotJob = copy(status = ScreenshotStatus.PROCESSING)
    fun markAsCompleted(url: String): ScreenshotJob = copy(status = ScreenshotStatus.COMPLETED, resultUrl = url)
}
```

### Value Objects

```kotlin
data class ScreenshotRequest(
    val url: String,
    val format: ScreenshotFormat,
    val width: Int = 1200,
    val height: Int = 800,
    val fullPage: Boolean = false,
    val waitTime: Int = 1000
) {
    init {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
    }
}
```

### Repository Interfaces

```kotlin
interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun save(user: User): User
    suspend fun update(user: User): User
}

interface ScreenshotRepository {
    suspend fun save(job: ScreenshotJob): ScreenshotJob
    suspend fun findById(id: String): ScreenshotJob?
    suspend fun findByUserId(userId: String, limit: Int = 50): List<ScreenshotJob>
}
```

### Domain Services

```kotlin
interface RateLimitingService {
    suspend fun checkRateLimit(userId: String, planId: String): RateLimitResult
    suspend fun recordRequest(userId: String)
}
```

## 🔧 Application Layer

Contains **use cases** that orchestrate the flow of data to and from entities.

### Use Cases

```kotlin
class TakeScreenshotUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository
) : UseCase<ScreenshotRequest, ScreenshotResponse> {
    
    override suspend fun invoke(request: ScreenshotRequest): ScreenshotResponse {
        // 1. Create screenshot job
        val job = ScreenshotJob.create(request)
        
        // 2. Save to repository
        screenshotRepository.save(job)
        
        // 3. Queue for processing
        queueRepository.enqueue(job)
        
        return ScreenshotResponse(
            jobId = job.id,
            status = job.status,
            estimatedTime = calculateEstimatedTime()
        )
    }
}
```

### Input/Output Ports

```kotlin
// Output ports (implemented by infrastructure)
interface StorageOutputPort {
    suspend fun store(data: ByteArray, filename: String): String
    suspend fun retrieve(filename: String): ByteArray?
}

interface UsageTrackingPort {
    suspend fun trackUsage(userId: String, creditsUsed: Int)
    suspend fun getMonthlyUsage(userId: String): UsageStatistics
}
```

## 🔌 Infrastructure Layer

Implements **external concerns** and provides concrete implementations.

### Input Adapters (Controllers)

```kotlin
@RestController
class ScreenshotController {
    
    @PostMapping("/api/v1/screenshot")
    suspend fun createScreenshot(
        @RequestBody request: CreateScreenshotRequest,
        principal: ApiKeyPrincipal
    ): ResponseEntity<ScreenshotResponse> {
        
        val screenshotRequest = request.toDomain()
        val response = takeScreenshotUseCase(screenshotRequest)
        
        return ResponseEntity.ok(response.toDto())
    }
}
```

### Output Adapters (Repositories)

```kotlin
class PostgreSQLUserRepository(
    private val database: Database
) : UserRepository {
    
    override suspend fun findById(id: String): User? = withContext(Dispatchers.IO) {
        Users.select { Users.id eq id }
            .map { it.toDomain() }
            .singleOrNull()
    }
    
    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        Users.insert {
            it[id] = user.id
            it[email] = user.email
            it[name] = user.name
            it[creditsRemaining] = user.creditsRemaining
        }
        user
    }
}
```

### External Service Adapters

```kotlin
class ScreenshotServiceImpl(
    private val browserPoolManager: BrowserPoolManager,
    private val storagePort: StorageOutputPort
) : ScreenshotService {
    
    override suspend fun takeScreenshot(request: ScreenshotRequest): String {
        val browser = browserPoolManager.acquireBrowser()
        try {
            val screenshot = browser.newPage().screenshot(request.toPlaywrightOptions())
            return storagePort.store(screenshot, generateFilename(request))
        } finally {
            browserPoolManager.releaseBrowser(browser)
        }
    }
}
```

## 🌊 Data Flow

### Screenshot Creation Flow

```
1. HTTP Request → ScreenshotController
2. Controller → TakeScreenshotUseCase
3. UseCase → ScreenshotRepository.save()
4. UseCase → QueueRepository.enqueue()
5. Response ← Controller ← UseCase
6. Background: ScreenshotWorker processes queue
7. Worker → ScreenshotService.takeScreenshot()
8. Worker → StoragePort.store()
9. Worker → ScreenshotRepository.update()
```

### Authentication Flow

```
1. HTTP Request with API Key → Security Plugin
2. Security → ValidateApiKeyUseCase
3. UseCase → ApiKeyRepository.findByHash()
4. UseCase → LogUsageUseCase (audit trail)
5. Principal Created → Controller Access Granted
```

### Rate Limiting Flow

```
1. Request → RateLimitPlugin
2. Plugin → RateLimitingService.checkRateLimit()
3. Service → CachePort.get() (Redis/InMemory)
4. Service → Apply token bucket algorithm
5. Service → CachePort.set() (update counters)
6. Allow/Deny → Continue/Reject Request
```

## 🎨 Design Patterns

### Repository Pattern
- **Interface**: Defined in domain layer
- **Implementation**: In infrastructure layer
- **Purpose**: Abstract data access, enable testing with mocks

### Use Case Pattern
- **Single Responsibility**: Each use case handles one business operation
- **Input/Output**: Clear request/response models
- **Testability**: Easy to unit test business logic

### Adapter Pattern
- **Input Adapters**: Convert external requests to domain models
- **Output Adapters**: Implement domain interfaces with external services
- **Purpose**: Isolate domain from external changes

### Factory Pattern
- **AuthProviderFactory**: Creates authentication providers
- **StorageFactory**: Creates storage adapters
- **CacheFactory**: Creates cache implementations

### Strategy Pattern
- **Authentication**: Multiple providers (Local, Clerk, JWT)
- **Storage**: Multiple backends (Local, S3, GCS)
- **Cache**: Multiple implementations (Redis, InMemory)

### Observer Pattern
- **Webhooks**: Notify external systems of events
- **Metrics**: Track system performance
- **Audit Logs**: Record all important actions

## 🛠️ Technology Stack

### Core Framework
- **Ktor**: Lightweight, coroutine-based web framework
- **Kotlin Coroutines**: Asynchronous programming
- **Koin**: Dependency injection framework

### Database & Persistence
- **PostgreSQL**: Primary database for production
- **Exposed ORM**: Type-safe database access
- **H2**: In-memory database for testing
- **Redis**: Caching and job queues

### Screenshot Generation
- **Microsoft Playwright**: Browser automation
- **Chromium**: Headless browser engine
- **PDF Generation**: Built-in Chromium PDF support

### Authentication & Security
- **JWT**: Token-based authentication
- **API Keys**: Service-to-service authentication
- **Clerk**: Third-party authentication provider
- **BCrypt**: Password hashing

### Monitoring & Observability
- **SLF4J + Logback**: Structured logging
- **Micrometer**: Application metrics
- **Health Checks**: System status monitoring

## 📈 Scalability Strategy

### Horizontal Scaling

```kotlin
class WorkerManager {
    // Auto-scaling based on queue depth
    private suspend fun monitorAndScale() {
        val queueSize = queueRepository.size()
        val activeWorkers = workers.size
        
        when {
            queueSize > activeWorkers * 5 -> scaleUp()
            queueSize == 0L && activeWorkers > minWorkers -> scaleDown()
        }
    }
}
```

### Caching Strategy

```kotlin
// Multi-level caching
class CacheFactory {
    fun createRateLimitCache(): CachePort = 
        RedisCacheAdapter(ttl = 5.minutes) // Short-term
    
    fun createUsageCache(): CachePort = 
        RedisCacheAdapter(ttl = 30.minutes) // Medium-term
}
```

### Database Scaling
- **Read Replicas**: Separate read/write databases
- **Connection Pooling**: Efficient database connections
- **Query Optimization**: Indexed columns, efficient queries

### Background Processing
- **Job Queues**: Redis-based job distribution
- **Worker Pools**: Multiple screenshot workers
- **Circuit Breakers**: Fault tolerance patterns

## 🔐 Security Architecture

### Authentication Layers
1. **API Key Authentication**: Service-to-service
2. **JWT Authentication**: User sessions
3. **Multi-Provider Auth**: External providers (Clerk)

### Authorization Model
```kotlin
enum class Permission {
    SCREENSHOT_CREATE,
    SCREENSHOT_READ,
    SCREENSHOT_LIST,
    API_KEY_MANAGE,
    ADMIN_ACCESS
}

data class ApiKey(
    val permissions: Set<Permission>,
    val rateLimit: Int,
    val expiresAt: Instant?
)
```

### Rate Limiting
- **Token Bucket Algorithm**: Smooth rate limiting
- **Per-User Limits**: Based on subscription plan
- **Global Limits**: System protection
- **Burst Handling**: Allow temporary spikes

### Audit Trail
```kotlin
data class UsageLog(
    val userId: String,
    val action: UsageLogAction,
    val apiKeyId: String?,
    val screenshotId: String?,
    val creditsUsed: Int,
    val metadata: Map<String, String>,
    val timestamp: Instant
)
```

## 🔄 Extension Points

### Adding New Authentication Providers
```kotlin
// 1. Implement AuthProvider interface
class GoogleAuthProvider : AuthProvider {
    override suspend fun validateToken(token: String): AuthResult?
}

// 2. Register in factory
class AuthProviderFactory {
    fun getProvider(name: String): AuthProvider? = when(name) {
        "google" -> GoogleAuthProvider()
        // ...
    }
}
```

### Adding New Storage Backends
```kotlin
// 1. Implement StorageOutputPort
class GCSStorageAdapter : StorageOutputPort {
    override suspend fun store(data: ByteArray, filename: String): String
}

// 2. Register in factory
class StorageFactory {
    fun create(config: StorageConfig): StorageOutputPort = when(config.type) {
        "gcs" -> GCSStorageAdapter(config)
        // ...
    }
}
```

### Adding New Screenshot Features
```kotlin
// 1. Extend ScreenshotRequest
data class ScreenshotRequest(
    // existing fields...
    val customCSS: String? = null,
    val mobileDevice: MobileDevice? = null
)

// 2. Update ScreenshotService implementation
class ScreenshotServiceImpl {
    override suspend fun takeScreenshot(request: ScreenshotRequest): String {
        // Handle new features
        if (request.customCSS != null) {
            page.addStyleTag(request.customCSS)
        }
    }
}
```

## 🎯 Future Architecture Considerations

### Microservices Evolution
- **Screenshot Service**: Independent screenshot processing
- **User Service**: Authentication and user management
- **Billing Service**: Credit management and payments
- **API Gateway**: Request routing and rate limiting

### Event-Driven Architecture
```kotlin
// Domain events
sealed class DomainEvent {
    data class ScreenshotCompleted(val jobId: String, val userId: String) : DomainEvent()
    data class CreditsDeducted(val userId: String, val amount: Int) : DomainEvent()
}

// Event handlers
class ScreenshotCompletedHandler {
    suspend fun handle(event: ScreenshotCompleted) {
        // Send webhook, update analytics, etc.
    }
}
```

### CQRS Implementation
- **Command Side**: Write operations (mutations)
- **Query Side**: Read operations (queries)
- **Event Store**: Audit trail and event sourcing

---

*This architecture enables the Screenshot API to be maintainable, testable, and scalable while keeping business logic independent of external frameworks and technologies.*