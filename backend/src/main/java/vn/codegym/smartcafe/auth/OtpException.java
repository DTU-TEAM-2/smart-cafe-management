package vn.codegym.smartcafe.auth;

public class OtpException extends RuntimeException {
    private final Long retryAfterSeconds;

    public OtpException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public OtpException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
