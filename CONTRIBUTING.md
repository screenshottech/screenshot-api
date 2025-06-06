# 🤝 Contributing to Screenshot API

We love your input! We want to make contributing to Screenshot API as easy and transparent as possible, whether it's:

- Reporting bugs
- Discussing the current state of the code
- Submitting fixes
- Proposing new features
- Becoming a maintainer

## 🌟 Development Philosophy

This project follows Clean Architecture principles and values:

- **Domain-First**: Business logic is independent of frameworks
- **Test-Driven**: Features should be well-tested
- **Developer Experience**: Tools and documentation should be excellent
- **Performance**: Efficiency matters at scale
- **Maintainability**: Code should be readable and modular

## 🚀 Quick Start for Contributors

### 1. Fork & Clone
```bash
# Fork the repository on GitHub
git clone https://github.com/screenshot-api-dev/screenshot-api.git
cd screenshot-api
```

### 2. Set Up Development Environment
```bash
# Start development environment
./docker/start.sh --cache

# Or run locally
./gradlew run
```

### 3. Verify Setup
```bash
# Run tests
./gradlew test

# Check health
curl http://localhost:8080/health
```

## 🏗️ Architecture Guidelines

### Clean Architecture Layers

```
core/
├── domain/          # Business entities, pure domain logic
├── usecases/        # Application business rules
└── ports/           # Interfaces for external dependencies

infrastructure/      # External concerns
├── adapters/        # Implementation of ports
├── config/          # Configuration
└── services/        # Infrastructure services
```

### Domain Rules
- **No Dependencies**: Domain layer has NO external dependencies
- **Pure Functions**: Domain logic should be testable in isolation
- **Immutable Entities**: Use data classes with copy() for changes
- **Repository Pattern**: Data access through interfaces

### Use Case Guidelines
- **Single Responsibility**: One use case per business operation
- **Input/Output Models**: Clear request/response data classes
- **Error Handling**: Use domain exceptions, not infrastructure exceptions

## 🧪 Testing Standards

### Test Structure
```kotlin
class TakeScreenshotUseCaseTest {
    @Test
    fun `should create screenshot job successfully`() {
        // Given
        val request = ScreenshotRequest(...)
        
        // When
        val result = useCase.invoke(request)
        
        // Then
        assertThat(result.jobId).isNotEmpty()
    }
}
```

### Testing Requirements
- **Unit Tests**: Core domain logic must be unit tested
- **Integration Tests**: API endpoints and repositories
- **Contract Tests**: External service interactions
- **Performance Tests**: For critical paths

### Running Tests
```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "TakeScreenshotUseCaseTest"

# Integration tests only
./gradlew integrationTest
```

## 📝 Code Style

### Kotlin Guidelines
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for consistent formatting
- Prefer immutable data structures
- Use meaningful names for variables and functions

### Code Formatting
```bash
# Check formatting
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat
```

### Example Code Style
```kotlin
// Good
data class ScreenshotRequest(
    val url: String,
    val format: ScreenshotFormat,
    val width: Int = 1200,
    val height: Int = 800
) {
    init {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(width > 0) { "Width must be positive" }
    }
}

// Use case implementation
class TakeScreenshotUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository
) : UseCase<ScreenshotRequest, ScreenshotResponse> {
    
    override suspend fun invoke(request: ScreenshotRequest): ScreenshotResponse {
        val job = ScreenshotJob.create(request)
        screenshotRepository.save(job)
        queueRepository.enqueue(job)
        
        return ScreenshotResponse(
            jobId = job.id,
            status = job.status
        )
    }
}
```

## 🐛 Bug Reports

### Before Submitting
1. Check existing [issues](https://github.com/screenshot-api-dev/screenshot-api/issues)
2. Verify the bug with latest version
3. Try to reproduce with minimal example

### Bug Report Template
```markdown
**Describe the bug**
Clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Send POST request to '/api/v1/screenshot'
2. With payload: `{"url": "...", "format": "PNG"}`
3. See error

**Expected behavior**
What you expected to happen.

**Environment:**
 - OS: [e.g. macOS, Ubuntu]
 - Docker version: [e.g. 20.10.8]
 - API version: [e.g. 1.0.0]

**API Request:**
```bash
curl -X POST "http://localhost:8080/api/v1/screenshot" \
  -H "Authorization: Bearer sk_test_..." \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com"}'
```

**Error Response:**
```json
{
  "error": {
    "code": "...",
    "message": "..."
  }
}
```
```

## ✨ Feature Requests

### Feature Request Process
1. **Search Existing**: Check if feature already requested
2. **Use Case**: Describe the problem you're solving
3. **Proposal**: Suggest implementation approach
4. **Discuss**: Engage with maintainers for feedback

### Feature Request Template
```markdown
**Is your feature request related to a problem?**
Clear description of the problem.

**Describe the solution you'd like**
Clear description of what you want to happen.

**API Design (if applicable)**
```bash
# Proposed endpoint
POST /api/v1/screenshot/batch
{
  "urls": ["https://example1.com", "https://example2.com"],
  "format": "PNG"
}
```

**Alternatives considered**
Other solutions you've considered.

**Additional context**
Screenshots, mockups, or additional context.
```

## 🔧 Development Workflow

### Branch Naming
- `feature/add-batch-processing`
- `fix/rate-limit-header-issue`
- `docs/update-api-reference`
- `refactor/improve-error-handling`

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):

```bash
feat: add batch screenshot processing
fix: correct rate limit header calculation
docs: update API reference for webhooks
refactor: extract screenshot validation logic
test: add integration tests for PDF generation
```

### Pull Request Process
1. **Fork** the repository
2. **Create** feature branch from `main`
3. **Implement** your changes with tests
4. **Run** tests and formatting checks
5. **Update** documentation if needed
6. **Submit** pull request with clear description

### PR Template
```markdown
## What does this PR do?
Brief description of changes.

## How to test?
```bash
# Steps to test the changes
./gradlew test
curl -X POST "http://localhost:8080/api/v1/..."
```

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Code formatted (`./gradlew ktlintFormat`)
- [ ] All tests pass (`./gradlew test`)
- [ ] No breaking changes (or clearly documented)

## Related Issues
Fixes #123
Related to #456
```

## 🏷️ Release Process

### Version Numbering
We use [Semantic Versioning](https://semver.org/):
- **MAJOR**: Breaking API changes
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, backwards compatible

### Release Checklist
- [ ] All tests pass
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version bumped in build.gradle.kts
- [ ] Docker images built and tested
- [ ] Performance benchmarks verified

## 🛡️ Security

### Reporting Security Vulnerabilities
**DO NOT** create public issues for security vulnerabilities.

Instead:
1. Email: screenshotapi.dev@gmail.com
2. Include detailed description
3. Steps to reproduce
4. Potential impact assessment

### Security Guidelines
- Never commit secrets or API keys
- Validate all user inputs
- Use secure defaults in configuration
- Follow OWASP security practices

## 🎯 Areas for Contribution

### High Impact, Low Effort
- 📝 Improve documentation and examples
- 🐛 Fix reported bugs
- 🧪 Add test coverage
- 🎨 Improve error messages

### High Impact, Medium Effort
- 🔍 Add OCR integration
- 📱 Mobile device simulation
- ⚡ Performance optimizations
- 📊 Enhanced monitoring

### High Impact, High Effort
- 🎥 Video/GIF capture
- 🌍 Multi-region deployment
- 🔄 Auto-scaling features
- 📦 SDK development

## 📚 Learning Resources

### Clean Architecture
- [Clean Architecture by Robert Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)

### Kotlin & Ktor
- [Ktor Documentation](https://ktor.io/docs/)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

### Domain-Driven Design
- [DDD Reference](https://domainlanguage.com/ddd/reference/)
- [Implementing DDD](https://www.oreilly.com/library/view/implementing-domain-driven-design/9780133039900/)

## 🙋‍♂️ Getting Help

### Communication Channels
- 💬 [GitHub Discussions](https://github.com/screenshot-api-dev/screenshot-api/discussions)
- 🐛 [GitHub Issues](https://github.com/screenshot-api-dev/screenshot-api/issues)
- 📧 Email: screenshotapi.dev@gmail.com

### Code Review
- Be respectful and constructive
- Explain the "why" behind suggestions
- Offer alternatives when pointing out problems
- Celebrate good practices and learning

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

**Thank you for contributing! 🎉**

Every contribution, no matter how small, makes this project better for the entire community.