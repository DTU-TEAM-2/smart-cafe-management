package vn.codegym.smartcafe.menu;

import org.springframework.http.HttpStatus;

final class MenuException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    MenuException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    HttpStatus status() { return status; }
    String code() { return code; }
}
