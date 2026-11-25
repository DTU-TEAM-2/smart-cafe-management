package vn.codegym.smartcafe.profile;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.codegym.smartcafe.auth.*;
import vn.codegym.smartcafe.home.AccountReader;
import vn.codegym.smartcafe.home.HomeAccount;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fault-injected unit checks; real authenticated HTTP/MySQL evidence is ProfileMySqlTest/Newman. */
class ProfileFailureTest {
    @Test void databaseAndTransactionFailureMapToSanitized503() throws Exception {
        var service = mock(ProfileService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProfileController(service)).setControllerAdvice(new ProfileErrors()).build();
        for (RuntimeException error : new RuntimeException[]{new DataAccessResourceFailureException("private SQL password details"),
                new CannotCreateTransactionException("private JDBC credentials")}) {
            doThrow(error).when(service).get(null);
            mvc.perform(get("/api/v1/profiles/me")).andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("PROFILE_UNAVAILABLE"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        }
    }

    @Test void failedCommitNeverIssuesOrReturnsReplacementBearer() {
        var accounts = mock(AccountReader.class);
        var repository = mock(ProfileRepository.class);
        var auth = mock(AuthRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var tokens = mock(TokenService.class);
        var manager = mock(PlatformTransactionManager.class);
        var tx = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(tx);
        doThrow(new TransactionSystemException("synthetic failed commit")).when(manager).commit(tx);
        var user = new UserRecord(1L, "synthetic", "synthetic-hash", "Synthetic", "synthetic@example.test", null,
                "STAFF", "ACTIVE", null, 0, Instant.now());
        var home = new HomeAccount("1", "synthetic", "Synthetic", "synthetic@example.test", null, "STAFF", null, null, null, null, null);
        when(accounts.byId(1)).thenReturn(Optional.of(new AccountReader.Account(home, Instant.now(), false, 0, 0)));
        when(auth.findUserById(1L)).thenReturn(Optional.of(user));
        when(encoder.matches("CurrentAa1", "synthetic-hash")).thenReturn(true);
        when(encoder.encode("ChangedBb2")).thenReturn("changed-hash");
        when(auth.updateUserPassword(1L, "changed-hash", 0)).thenReturn(1);
        var service = new ProfileService(accounts, repository, auth, encoder, tokens, Clock.systemUTC(), manager);
        var request = new ProfileModels.PasswordChange(java.util.Map.of("currentPassword", "CurrentAa1",
                "newPassword", "ChangedBb2", "confirmNewPassword", "ChangedBb2"));
        assertThatThrownBy(() -> service.changePassword(user, request)).isInstanceOf(TransactionSystemException.class);
        verifyNoInteractions(tokens);
    }
}
