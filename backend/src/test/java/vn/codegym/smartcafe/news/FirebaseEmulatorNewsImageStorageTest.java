package vn.codegym.smartcafe.news;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseEmulatorNewsImageStorageTest {
    @Test
    void rejectsCloudEndpointsCredentialsAndAmbiguousUrlsBeforeNetworkAccess() {
        for (String host : new String[]{"https://storage.googleapis.com", "http://example.com:9199",
                "http://127.0.0.1.evil.invalid:9199", "http://user@127.0.0.1:9199", "http://127.0.0.1:9199/path",
                "http://127.0.0.1:9199?redirect=x", "http://127.0.0.1:9199#x", "http://127.0.0.1"}) {
            assertThatThrownBy(() -> new FirebaseEmulatorNewsImageStorage(host,
                    "jdbc:mysql://127.0.0.1:3306/cafe_test"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesSystemDatabasesEvenWithAValidLocalEndpoint() {
        for (String url : new String[]{"jdbc:mysql://127.0.0.1:3306/smart_cafe",
                "jdbc:mysql://127.0.0.1:3306/cafe_test_extra", "jdbc:mysql://127.0.0.1:3306/cafe?x=_test"}) {
            assertThatThrownBy(() -> new FirebaseEmulatorNewsImageStorage("http://127.0.0.1:19199", url))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
