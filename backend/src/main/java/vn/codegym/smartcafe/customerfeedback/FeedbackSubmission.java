package vn.codegym.smartcafe.customerfeedback;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Cloud calls are outside the DB transaction. Remove successful uploads if persistence fails. */
@Service
public class FeedbackSubmission {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FeedbackSubmission.class);
    private final FeedbackImages images;
    private final FirebaseFeedbackStorage storage;
    private final CustomerFeedbackService service;
    public FeedbackSubmission(FeedbackImages images, FirebaseFeedbackStorage storage, CustomerFeedbackService service) {
        this.images = images; this.storage = storage; this.service = service;
    }
    public String submit(CustomerFeedbackController.Command command, List<MultipartFile> files) {
        var prepared = images.validate(files);
        var uploaded = new ArrayList<FirebaseFeedbackStorage.Uploaded>();
        try {
            for (var image : prepared) uploaded.add(storage.upload(image));
            return service.submitWithImages(command, uploaded);
        } catch (RuntimeException error) {
            for (var image : uploaded) {
                try { storage.delete(image); }
                catch (RuntimeException cleanup) { LOG.warn("Không xóa được ảnh test/phản hồi sau khi gửi thất bại; cần kiểm tra cleanup tại provider."); }
            }
            throw error;
        }
    }
}
