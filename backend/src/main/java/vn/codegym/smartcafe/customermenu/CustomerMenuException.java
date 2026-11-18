package vn.codegym.smartcafe.customermenu;

import org.springframework.http.HttpStatus;

final class CustomerMenuException extends RuntimeException {
    private final HttpStatus status;

    CustomerMenuException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
