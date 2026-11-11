package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;

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
    void rejectsTooLongPasswords() {
        String longPassword = "A1a" + "x".repeat(65);
        assertFalse(PasswordPolicy.isValid(longPassword));
    }
}
