package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void acceptsValidPassword() {
        assertTrue(PasswordPolicy.isValid("Admin@123"));
        assertTrue(PasswordPolicy.isValid("SecureP@ss1"));
        assertTrue(PasswordPolicy.isValid("Cafe2026Strong"));
    }

    @Test
    void rejectsNullOrEmpty() {
        assertFalse(PasswordPolicy.isValid(null));
        assertFalse(PasswordPolicy.isValid(""));
        assertFalse(PasswordPolicy.isValid("   "));
    }

    @Test
    void rejectsShortPasswords() {
        assertFalse(PasswordPolicy.isValid("Ab1!")); // 4 chars
        assertFalse(PasswordPolicy.isValid("Abcdef1")); // 7 chars
    }

    @Test
    void rejectsPasswordsWithoutUppercase() {
        assertFalse(PasswordPolicy.isValid("password123"));
    }

    @Test
    void rejectsPasswordsWithoutLowercase() {
        assertFalse(PasswordPolicy.isValid("PASSWORD123"));
    }

    @Test
    void rejectsPasswordsWithoutDigits() {
        assertFalse(PasswordPolicy.isValid("PasswordNoNumber"));
    }

    @Test
    void rejectsTooLongPasswordsByCharacterCount() {
        String longPassword = "A1a" + "x".repeat(65);
        assertFalse(PasswordPolicy.isValid(longPassword));
    }

    @Test
    void validatesUtf8ByteBoundaryForBcryptCompatibility() {
        // Exactly 72 UTF-8 bytes and <= 64 characters: valid
        // 8 non-ASCII 2-byte characters (16 bytes) + 56 ASCII chars (56 bytes) = 64 chars, 72 bytes
        String exactly72Bytes = "Aa1" + "é".repeat(8) + "x".repeat(53);
        assertEquals(64, exactly72Bytes.length());
        assertEquals(72, exactly72Bytes.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(PasswordPolicy.isValid(exactly72Bytes));

        // 73 UTF-8 bytes and <= 64 characters: rejected because BCrypt has 72-byte max limit
        String exactly73Bytes = "Aa1" + "é".repeat(9) + "x".repeat(52);
        assertEquals(64, exactly73Bytes.length());
        assertEquals(73, exactly73Bytes.getBytes(StandardCharsets.UTF_8).length);
        assertFalse(PasswordPolicy.isValid(exactly73Bytes));

        // 28 characters with Vietnamese multi-byte diacritics exceeding 72 bytes (exactly 78 bytes)
        // Reviewer test case: 28 ký tự có dấu, dài 78 byte
        String vietnamese28Chars78Bytes = "Ậ".repeat(25) + "Aa1";
        assertEquals(28, vietnamese28Chars78Bytes.length());
        assertEquals(78, vietnamese28Chars78Bytes.getBytes(StandardCharsets.UTF_8).length);
        assertFalse(PasswordPolicy.isValid(vietnamese28Chars78Bytes), "Must be rejected by PasswordPolicy to prevent BCrypt crash");
    }
}
