# Unit Test Template (Java, JUnit 5 style)

Example structure and style for readable unit tests.

```java
package com.example.ezeconomy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    @Test
    void deposit_increasesBalanceByAmount() {
        // Arrange
        Account account = new AccountBuilder().withBalance(100L).build();
        AccountService service = new AccountService();

        // Act
        service.deposit(account, 25L);

        // Assert
        assertEquals(125L, account.getBalance());
    }
}
```

Guidelines:

- Use `Arrange / Act / Assert` blocks with blank lines between them for readability.
- Use builder/fixture helpers to keep setup concise.
- Name tests to convey behavior (what, when, expected).
- Keep tests deterministic and fast.
