package vn.codegym.smartcafe.profile;

import org.springframework.http.HttpStatus;

final class ProfileException extends RuntimeException {
    final HttpStatus status;
    final String code;

    ProfileException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
}
