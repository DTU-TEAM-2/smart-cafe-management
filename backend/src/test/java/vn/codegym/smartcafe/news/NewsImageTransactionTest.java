package vn.codegym.smartcafe.news;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises Spring's completion callbacks for definite rollback versus an uncertain commit. */
class NewsImageTransactionTest {
    private final NewsImageStorage storage = mock(NewsImageStorage.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec insert = mock(JdbcClient.StatementSpec.class);
    private final CompletionManager transactions = new CompletionManager();
    private final NewsImageService images = new NewsImageService(jdbc, storage, mock(NewsReaderAccess.class), transactions);
    private static final String ASSET = "news/00000000-0000-0000-0000-000000000001.png";

    @BeforeEach
    void setup() {
        when(storage.upload(any(), eq("image/png"))).thenReturn(new NewsImageStorage.StoredImage(ASSET, "https://example.invalid/news.png"));
        when(jdbc.sql(anyString())).thenReturn(insert);
        when(insert.param(any())).thenReturn(insert);
        when(insert.update(any(KeyHolder.class), eq("id"))).thenAnswer(call -> {
            call.getArgument(0, KeyHolder.class).getKeyList().add(Map.of("id", 1L));
            return 1;
        });
    }

    @Test
    void successfulCommitRetainsImage() throws Exception {
        assertThat(images.upload(png()).mediaId()).isEqualTo("1");
        verify(storage, never()).delete(anyString());
        assertThat(transactions.released).isTrue();
    }

    @Test
    void failureToBeginCompensatesAnImageThatCouldNotHaveCommitted() throws Exception {
        transactions.beginFailure = new CannotCreateTransactionException("Test begin failure");
        assertThatThrownBy(() -> images.upload(png())).isSameAs(transactions.beginFailure);
        verify(storage).delete(ASSET);
        verifyNoInteractions(jdbc);
    }

    @Test
    void confirmedRollbackDeletesOutsideTransactionAndKeepsOriginalFailureIfDeleteFails() throws Exception {
        var failure = new DataAccessResourceFailureException("Test INSERT failure");
        when(jdbc.sql(anyString())).thenThrow(failure);
        doAnswer(call -> {
            assertThat(transactions.released).isTrue();
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("Test delete failure");
        }).when(storage).delete(ASSET);
        assertThatThrownBy(() -> images.upload(png())).isSameAs(failure);
        verify(storage).delete(ASSET);
    }

    @Test
    void unknownCommitRetainsObjectForReconciliation() throws Exception {
        transactions.commitFailure = new TransactionSystemException("Test lost commit acknowledgement");
        assertThatThrownBy(() -> images.upload(png())).isSameAs(transactions.commitFailure);
        assertThat(transactions.released).isTrue();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void failedRollbackAlsoRetainsObjectForReconciliation() throws Exception {
        when(jdbc.sql(anyString())).thenThrow(new DataAccessResourceFailureException("Test INSERT failure"));
        transactions.rollbackFailure = new TransactionSystemException("Test lost rollback acknowledgement");
        assertThatThrownBy(() -> images.upload(png())).isSameAs(transactions.rollbackFailure);
        assertThat(transactions.released).isTrue();
        verify(storage, never()).delete(anyString());
    }

    private MockMultipartFile png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
    }

    private static final class CompletionManager extends AbstractPlatformTransactionManager {
        RuntimeException beginFailure, commitFailure, rollbackFailure;
        boolean released;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            if (beginFailure != null) throw beginFailure;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (commitFailure != null) throw commitFailure;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            if (rollbackFailure != null) throw rollbackFailure;
        }
        @Override protected void doCleanupAfterCompletion(Object transaction) { released = true; }
    }
}
