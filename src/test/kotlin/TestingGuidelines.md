# Testing Guidelines - Screenshot API

## Patrón AAA (Arrange-Act-Assert)

Todos los tests en este proyecto deben seguir el patrón **AAA** para mantener la claridad y consistencia del código.

### 🎯 **Estructura AAA**

```kotlin
@Test
fun `should describe what the test does`() {
    // Arrange - Preparar datos y configurar mocks
    val input = "test-data"
    val expected = "expected-result"
    every { mockService.someMethod(input) } returns expected
    
    // Act - Ejecutar la acción que estamos probando
    val result = serviceUnderTest.methodToTest(input)
    
    // Assert - Verificar el resultado
    assertEquals(expected, result, "Should return expected result")
    verify { mockService.someMethod(input) }
}
```

### 📋 **Reglas Obligatorias**

#### **1. Comentarios de Sección**
- **SIEMPRE** incluir comentarios `// Arrange`, `// Act`, `// Assert`
- Ayuda a identificar rápidamente cada fase del test
- Mejora la legibilidad y mantenimiento

#### **2. Separación Visual**
- Línea en blanco entre cada sección
- Facilita la lectura del código
- Hace que la estructura sea obvia

#### **3. Mensajes Descriptivos**
- Todos los `assert` deben incluir mensaje descriptivo
- Explica qué debería pasar en caso de fallo
- Formato: `"Should [expected behavior]"`

```kotlin
// ✅ CORRECTO
assertEquals(expectedToken, result, "Should return token from HMAC port")
assertTrue(isValid, "Should validate correct token")

// ❌ INCORRECTO
assertEquals(expectedToken, result)
assertTrue(isValid)
```

#### **4. Nombres de Variables Claros**
```kotlin
// ✅ CORRECTO
val validToken = "abc123"
val invalidToken = "xyz789"
val expectedFilename = "screenshots/2023/01/token.png"

// ❌ INCORRECTO
val token1 = "abc123"
val token2 = "xyz789"
val result = "screenshots/2023/01/token.png"
```

### 🔧 **Patrones Específicos por Tipo de Test**

#### **Tests de Delegación (Adapter Pattern)**
```kotlin
@Test
fun `methodName should delegate to dependency`() {
    // Arrange
    val input = "test-input"
    val expectedOutput = "expected-output"
    every { dependency.method(input) } returns expectedOutput
    
    // Act
    val result = adapterUnderTest.method(input)
    
    // Assert
    assertEquals(expectedOutput, result, "Should return result from dependency")
    verify { dependency.method(input) }
}
```

#### **Tests de Validación**
```kotlin
@Test
fun `validate should return true for valid input`() {
    // Arrange
    val validInput = createValidInput()
    
    // Act
    val result = validator.validate(validInput)
    
    // Assert
    assertTrue(result, "Should validate correct input")
}

@Test
fun `validate should return false for invalid input`() {
    // Arrange
    val invalidInput = createInvalidInput()
    
    // Act
    val result = validator.validate(invalidInput)
    
    // Assert
    assertFalse(result, "Should reject invalid input")
}
```

#### **Tests de Generación/Transformación**
```kotlin
@Test
fun `generate should create correct format`() {
    // Arrange
    val inputData = createTestData()
    val expectedFormat = "expected-format"
    
    // Act
    val result = generator.generate(inputData)
    
    // Assert
    assertTrue(result.startsWith(expectedFormat), "Should use correct format")
    assertEquals(32, result.length, "Should have correct length")
}
```

#### **Tests de Múltiples Escenarios**
```kotlin
@Test
fun `validate should handle different input scenarios`() {
    // Arrange
    val testCases = listOf(
        "valid-input-1" to true,
        "valid-input-2" to true,
        "invalid-input" to false,
        "" to false
    )
    
    testCases.forEach { (input, expected) ->
        // Act
        val result = validator.validate(input)
        
        // Assert
        assertEquals(expected, result, "Should handle input: $input")
    }
}
```

### 🎨 **Mejores Prácticas**

#### **1. Arrange: Preparación Clara**
```kotlin
// ✅ BUENO: Variables con nombres descriptivos
val validJobId = "job_123456789_abcd1234"
val testUserId = "user_123"
val screenshotCreationTime = 1672531200L
val jobType = JobType.SCREENSHOT

// ❌ MALO: Variables genéricas
val id = "job_123456789_abcd1234"  
val userId = "user_123"
val time = 1672531200L
val type = JobType.SCREENSHOT
```

#### **2. Act: Acción Única y Clara**
```kotlin
// ✅ BUENO: Una sola acción por test
val result = tokenService.generateToken(testJob)

// ❌ MALO: Múltiples acciones confusas
val token = tokenService.generateToken(testJob)
val filename = filenameService.createFilename(token)
val result = storageService.save(filename)
```

#### **3. Assert: Verificaciones Específicas**
```kotlin
// ✅ BUENO: Assertions específicas y descriptivas
assertEquals(32, token.length, "Token should be 32 characters")
assertTrue(token.matches(Regex("^[A-Za-z0-9_-]+$")), "Token should be URL-safe")
assertFalse(token.contains("+"), "Token should not contain '+' character")

// ❌ MALO: Assertion genérica
assertTrue(isValidToken(token))
```

### 🧪 **Casos Especiales**

#### **Exception Testing**
```kotlin
@Test
fun `method should throw exception for invalid input`() {
    // Arrange
    val invalidInput = createInvalidInput()
    
    // Act & Assert
    val exception = assertThrows<IllegalArgumentException> {
        service.processInput(invalidInput)
    }
    assertEquals("Invalid input provided", exception.message, "Should have correct error message")
}
```

#### **Mock Verification**
```kotlin
@Test
fun `method should call dependency with correct parameters`() {
    // Arrange
    val input = "test-input"
    every { dependency.process(any()) } returns "result"
    
    // Act
    service.performAction(input)
    
    // Assert
    verify { dependency.process(input) }
    verify(exactly = 1) { dependency.process(any()) }
}
```

### ⚠️ **Anti-Patrones a Evitar**

#### **❌ No mezclar Act y Assert**
```kotlin
// MALO
val result = service.process(input)
assertTrue(result.isValid)
val transformed = service.transform(result)
assertEquals("expected", transformed)
```

#### **❌ No reutilizar variables entre tests**
```kotlin
// MALO - Estado compartido
class ServiceTest {
    private var sharedResult: String = ""
    
    @Test
    fun test1() {
        sharedResult = service.method1()
        assertEquals("expected", sharedResult)
    }
}
```

#### **❌ No usar assertions sin mensajes**
```kotlin
// MALO
assertTrue(result)
assertEquals(expected, actual)
assertNotNull(value)
```

### 📚 **Recursos Adicionales**

- **Naming Convention**: `should [action] [expected outcome] [conditions]`
- **Test Data**: Usar builders o factories para datos complejos
- **Isolation**: Cada test debe ser independiente
- **Speed**: Tests unitarios deben ser rápidos (< 100ms)

### 🔍 **Checklist Pre-Commit**

Antes de hacer commit, verificar:

- [ ] ¿Todos los tests siguen el patrón AAA?
- [ ] ¿Hay comentarios `// Arrange`, `// Act`, `// Assert`?
- [ ] ¿Todos los assertions tienen mensajes descriptivos?
- [ ] ¿Los nombres de variables son claros y descriptivos?
- [ ] ¿Cada test verifica una sola cosa?
- [ ] ¿Los tests son independientes entre sí?

---

**Recuerda**: Tests claros = código mantenible = equipo feliz! 🎉