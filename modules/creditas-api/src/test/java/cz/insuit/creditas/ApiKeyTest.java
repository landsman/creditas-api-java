package cz.insuit.creditas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Ab12", " Ab12 ", " Ab12", "Ab 12", "Ab12​", "﻿Ab12", "\tAb12\n"})
    void keepsTheLettersAndDigitsOfAPastedKey(String pasted) {
        assertEquals("Ab12", new ApiKey(pasted).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " ", "​﻿"})
    void aBlankKeyIsRefused(String blank) {
        assertThrows(IllegalArgumentException.class, () -> new ApiKey(blank));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret64chars"})
    void neverPrintsTheKey(String key) {
        assertFalse(new ApiKey(key).toString().contains(key));
    }
}
