# JSONPath & Hamcrest Testing Examples

Reference documentation for JSONPath expressions and Hamcrest matchers in Spring Boot MockMvc tests.

**Resources:**
- [JSONPath GitHub](https://github.com/json-path/JsonPath)
- [JSONPath Online Evaluator](https://jsonpath.com/)

---

## Overview

**JSONPath** is a query language for JSON (similar to XPath for XML). It extracts data from JSON responses.

**Hamcrest** is a matcher library that provides readable assertions for test validation.

**Spring Test Integration:** The `jsonPath()` method combines both:
```java
.andExpect(jsonPath("$.field", hasSize(3)))
                      ↑            ↑
                 JSONPath      Hamcrest
                 extracts      validates
```

---

## Complete Test Class

This test class demonstrates common JSONPath expressions and Hamcrest matchers against a sample JSON response.

**Sample JSON Structure:**
```json
{
  "name": "Trading Platform",
  "version": "1.0",
  "stocks": [
    {"symbol": "AAPL", "price": 150.25, "volume": 1000000},
    {"symbol": "TSLA", "price": 245.80, "volume": 2000000},
    {"symbol": "GOOGL", "price": 2800.50, "volume": 500000}
  ],
  "metadata": {
    "updated": "2024-12-01",
    "source": "API"
  }
}
```

---
```java
package com.joakimcolloz.stocker.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demonstrates common JSONPath expressions with Hamcrest matchers for Spring Boot testing.
 * <p>
 * JSONPath is a query language for JSON (like XPath for XML) that extracts data from JSON.
 * Hamcrest provides readable matcher assertions for validating the extracted data.
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://github.com/json-path/JsonPath">JSONPath GitHub</a></li>
 *   <li><a href="https://jsonpath.com/">JSONPath Online Evaluator</a></li>
 * </ul>
 *
 */
@WebMvcTest(controllers = JsonPathExamplesTest.DummyController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@DisplayName("JSONPath Expression Examples")
public class JsonPathExamplesTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Dummy REST controller that returns sample JSON for testing JSONPath expressions.
     */
    @RestController
    static class DummyController {
        @GetMapping("/dummy")
        public String getDummyData() {
            return """
                {
                  "name": "Trading Platform",
                  "version": "1.0",
                  "stocks": [
                    {"symbol": "AAPL", "price": 150.25, "volume": 1000000},
                    {"symbol": "TSLA", "price": 245.80, "volume": 2000000},
                    {"symbol": "GOOGL", "price": 2800.50, "volume": 500000}
                  ],
                  "metadata": {
                    "updated": "2024-12-01",
                    "source": "API"
                  }
                }
                """;
        }
    }

    /**
     * Demonstrates root element access using "$" operator.
     * <p>
     * JSONPath: $.name, $.version
     * Result: Accesses top-level fields directly from root JSON object
     */
    @Test
    @DisplayName("$ - Root element access")
    void testRootElement() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trading Platform"))
                .andExpect(jsonPath("$.version").value("1.0"));
    }

    /**
     * Demonstrates array element access by zero-based index.
     * <p>
     * JSONPath: $.stocks[0].symbol
     * Result: Accesses specific elements in array by position
     * Use when: Order is guaranteed (e.g., sorted results)
     */
    @Test
    @DisplayName("$[index] - Array element by index")
    void testArrayByIndex() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.stocks[1].price").value(245.80))
                .andExpect(jsonPath("$.stocks[2].volume").value(500000));
    }

    /**
     * Demonstrates wildcard selector to extract field from ALL array elements.
     * <p>
     * JSONPath: $.stocks[*].symbol
     * Result: Creates array of all symbol values → ["AAPL", "TSLA", "GOOGL"]
     * Use when: Order doesn't matter, checking for presence of values
     * <p>
     * Note: $[*] extracts from all elements, then Hamcrest matcher validates the result
     */
    @Test
    @DisplayName("$[*] - All array elements (wildcard)")
    void testArrayWildcard() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                // Extract all symbols → ["AAPL", "TSLA", "GOOGL"]
                .andExpect(jsonPath("$.stocks[*].symbol", hasItem("AAPL")))
                .andExpect(jsonPath("$.stocks[*].symbol", hasItems("TSLA", "GOOGL")))
                .andExpect(jsonPath("$.stocks[*].symbol", hasSize(3)));
    }

    /**
     * Demonstrates recursive descent to find field anywhere in JSON tree.
     * <p>
     * JSONPath: $..source
     * Result: Searches entire JSON structure for 'source' field at any nesting level
     * Use when: Field path is unknown but field name is known
     */
    @Test
    @DisplayName("$..[field] - Recursive descent (search all levels)")
    void testRecursiveDescent() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                // Find 'source' anywhere in JSON tree
                .andExpect(jsonPath("$..source").value("API"));
    }

    /**
     * Demonstrates filter expressions to select array elements matching conditions.
     * <p>
     * JSONPath: $.stocks[?(@.price > 200)]
     * Syntax: [?(@.field operator value)]
     * Operators: >, <, ==, !=, >=, <=
     * Result: Returns filtered subset of array
     */
    @Test
    @DisplayName("$[?(@.field)] - Filter expressions")
    void testFilterExpressions() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                // Find stocks where price > 200
                .andExpect(jsonPath("$.stocks[?(@.price > 200)]", hasSize(2)))
                // Find stock with specific symbol
                .andExpect(jsonPath("$.stocks[?(@.symbol == 'AAPL')].price").value(150.25));
    }

    /**
     * Demonstrates common Hamcrest matchers for various assertion types.
     * <p>
     * Hamcrest matchers provide readable, expressive assertions:
     * - Collection matchers: hasSize(), hasItem(), everyItem()
     * - Numeric matchers: greaterThan(), lessThan()
     * - String matchers: startsWith(), containsString()
     * - Existence matchers: exists(), isNotEmpty()
     */
    @Test
    @DisplayName("Hamcrest matchers - Common assertions")
    void testHamcrestMatchers() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                // Array size
                .andExpect(jsonPath("$.stocks", hasSize(3)))
                // Contains specific items
                .andExpect(jsonPath("$.stocks[*].symbol", hasItem("AAPL")))
                // Every item matches condition
                .andExpect(jsonPath("$.stocks[*].volume", everyItem(greaterThan(0))))
                // Value in range
                .andExpect(jsonPath("$.stocks[0].price", greaterThan(100.0)))
                .andExpect(jsonPath("$.stocks[0].price", lessThan(200.0)))
                // String matchers
                .andExpect(jsonPath("$.name", startsWith("Trading")))
                .andExpect(jsonPath("$.name", containsString("Platform")))
                // Not null/empty
                .andExpect(jsonPath("$.metadata.updated").isNotEmpty())
                .andExpect(jsonPath("$.metadata.source").exists());
    }

    /**
     * Demonstrates accessing nested objects using dot notation.
     * <p>
     * JSONPath: $.metadata.source
     * Result: Navigates through object hierarchy to access deeply nested values
     */
    @Test
    @DisplayName("Nested object access")
    void testNestedObjects() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.updated").value("2024-12-01"))
                .andExpect(jsonPath("$.metadata.source").value("API"));
    }

    /**
     * Demonstrates array validation matchers.
     * <p>
     * Common array checks:
     * - isArray(): Verifies field is an array type
     * - hasSize(): Validates array length
     * - isNotEmpty(): Ensures array contains elements
     */
    @Test
    @DisplayName("Array length and emptiness checks")
    void testArrayChecks() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks").isArray())
                .andExpect(jsonPath("$.stocks", hasSize(3)))
                .andExpect(jsonPath("$.stocks").isNotEmpty());
    }
}
```

---

## Key Concepts

### JSONPath Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `$` | Root element | `$.name` |
| `.field` | Child field | `$.stocks.symbol` |
| `[index]` | Array element by index | `$.stocks[0]` |
| `[*]` | All array elements | `$.stocks[*].symbol` |
| `..field` | Recursive descent | `$..source` |
| `[?(@.x)]` | Filter expression | `$[?(@.price > 200)]` |

### Common Hamcrest Matchers

**Collections:**
- `hasSize(n)` - Array/collection has n elements
- `hasItem(x)` - Contains specific item
- `hasItems(x, y)` - Contains multiple items
- `everyItem(matcher)` - All items match condition

**Numeric:**
- `greaterThan(n)`, `lessThan(n)`
- `greaterThanOrEqualTo(n)`, `lessThanOrEqualTo(n)`

**Strings:**
- `startsWith(s)`, `endsWith(s)`
- `containsString(s)`
- `matchesPattern(regex)`

**Existence:**
- `exists()`, `doesNotExist()`
- `isNotEmpty()`, `isEmpty()`

### Best Practices

1. **Use `$[*]` with `hasItem()` when order doesn't matter**
```java
   .andExpect(jsonPath("$[*].symbol", hasItem("AAPL")))
```

2. **Use indexed access `$[0]` when order is guaranteed (sorted)**
```java
   .andExpect(jsonPath("$[0].timestamp").value(mostRecent))
```

3. **Extract to POJO for complex validations**
```java
   String json = mockMvc.perform(get("/endpoint"))
       .andReturn().getResponse().getContentAsString();
   MyResponse response = objectMapper.readValue(json, MyResponse.class);
   // Perform custom assertions
```

4. **Combine JSONPath and Hamcrest for expressive tests**
```java
   // JSONPath extracts → Hamcrest validates
   .andExpect(jsonPath("$.stocks[*].volume", everyItem(greaterThan(0))))
```