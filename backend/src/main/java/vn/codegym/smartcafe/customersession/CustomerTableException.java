package vn.codegym.smartcafe.customersession;

import org.springframework.http.HttpStatus;

final class CustomerTableException extends RuntimeException {
    private final HttpStatus status;

    CustomerTableException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
