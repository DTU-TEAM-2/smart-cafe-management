package vn.codegym.smartcafe.auth;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        byte[] utf8Bytes = password.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_UTF8_BYTES) {
            return false;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasUpper && hasLower && hasDigit;
    }
}
