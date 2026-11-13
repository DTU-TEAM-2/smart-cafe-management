package vn.codegym.smartcafe.news;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseNewsImageStorageTest {
    @Test
    void missingBucketFailsClosedWithoutCallingAnExternalService() {
        var storage = new FirebaseNewsImageStorage("", true);
        assertThatThrownBy(() -> storage.upload(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void foreignObjectNamesAreRejectedBeforeAnyCredentialLookup() {
        var storage = new FirebaseNewsImageStorage("", false);
        assertThatThrownBy(() -> storage.read("other-module/private.pdf"))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void freeOnlyPolicyRejectsConfiguredCloudBucketBeforeCredentialLookup() {
        var storage = new FirebaseNewsImageStorage("configured-but-not-authorized.appspot.com", false);
        assertThatThrownBy(() -> storage.upload(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.getStatusCode().value()).isEqualTo(503));
    }
}
