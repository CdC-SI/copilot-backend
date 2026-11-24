package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashUtilsTest {

    @Test
    void sha256IsTheLowercaseHexDigestWithLeadingZeros() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", HashUtils.sha256(""));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", HashUtils.sha256("hello"));
    }

    @Test
    void otherAlgorithmsAreSupportedAndUnknownOnesRejected() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", HashUtils.hash("hello", "MD5"));
        var e = assertThrows(RuntimeException.class, () -> HashUtils.hash("hello", "SHA-999"));
        assertEquals("Error: Unsupported hashing algorithm SHA-999", e.getMessage());
    }
}
