package vn.codegym.smartcafe.customerfeedback;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

/** Cloud calls are outside the DB transaction. Remove successful uploads only on confirmed rollback. */
@Service
public class FeedbackSubmission {
    private static final Logger LOG = LoggerFactory.getLogger(FeedbackSubmission.class);
    private final FeedbackImages images;
    private final FirebaseFeedbackStorage storage;
    private final CustomerFeedbackService service;
    private final PlatformTransactionManager transactionManager;

    public FeedbackSubmission(FeedbackImages images, FirebaseFeedbackStorage storage, CustomerFeedbackService service,
            PlatformTransactionManager transactionManager) {
        this.images = images;
        this.storage = storage;
        this.service = service;
        this.transactionManager = transactionManager;
    }

    public String submit(CustomerFeedbackController.Command command, List<MultipartFile> files) {
        if (command == null) {
            throw new IllegalArgumentException("Dữ liệu phản hồi không được để trống.");
        }
        CustomerFeedbackService.validateContent(command.content());
        var prepared = images.validate(files);
        var uploaded = new ArrayList<FirebaseFeedbackStorage.Uploaded>();
        try {
            for (var image : prepared) {
                uploaded.add(storage.upload(image));
            }
        } catch (RuntimeException uploadError) {
            cleanupBlobs(uploaded);
            throw uploadError;
        }

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        String feedbackId;
        try {
            feedbackId = service.submitWithImages(command, uploaded);
        } catch (RuntimeException executionError) {
            try {
                transactionManager.rollback(status);
            } catch (Exception rbEx) {
                LOG.warn("Lỗi khi rollback giao dịch phản hồi: {}", rbEx.getMessage());
            }
            cleanupBlobs(uploaded);
            throw executionError;
        }

        try {
            transactionManager.commit(status);
            return feedbackId;
        } catch (TransactionException commitError) {
            LOG.warn("Kết quả commit không rõ ràng; giữ lại blob để đối soát và không xóa: {}", commitError.getMessage());
            throw commitError;
        }
    }

    private void cleanupBlobs(List<FirebaseFeedbackStorage.Uploaded> uploaded) {
        for (var image : uploaded) {
            try {
                storage.delete(image);
            } catch (RuntimeException cleanup) {
                LOG.warn("Không xóa được ảnh test/phản hồi sau khi gửi thất bại; cần kiểm tra cleanup tại provider.");
            }
        }
    }
}
