package vn.codegym.smartcafe.customerfeedback;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedbackImagesTest {
    @Test void rejectsPixelBudgetEvenWhenCompressedFileAndDimensionsFit() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2001, 2000, BufferedImage.TYPE_BYTE_GRAY), "png", bytes);
        assertThat(bytes.size()).isLessThan(5 * 1024 * 1024);
        var file = new MockMultipartFile("images", "large.png", "image/png", bytes.toByteArray());
        assertThatThrownBy(() -> new FeedbackImages().validate(List.of(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4 triệu điểm ảnh");
    }
    static byte[] png() throws Exception {
        var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);return bytes.toByteArray();
    }
    @Test void validatesActualBytesRatherThanTrustingExtensionOrMime() throws Exception {
        var images=new FeedbackImages();
        var result=images.validate(List.of(new MockMultipartFile("images","not-trusted.exe","text/plain",png())));
        assertThat(result.getFirst().contentType()).isEqualTo("image/png");
        assertThat(result.getFirst().width()).isEqualTo(2);
        assertThatThrownBy(()->images.validate(List.of(new MockMultipartFile("images","fake.png","image/png","not-an-image".getBytes())))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsCountEmptyAndOversizedFilesBeforeProviderCalls() throws Exception {
        var validator=new FeedbackImages();
        var valid=new MockMultipartFile("images","test.png","image/png",png());
        assertThatThrownBy(()->validator.validate(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validator.validate(List.of(valid,valid,valid,valid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validator.validate(List.of(new MockMultipartFile("images",new byte[0])))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validator.validate(List.of(new MockMultipartFile("images",new byte[5*1024*1024+1])))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void removesEarlierUploadsIfLaterUploadFailsWithoutSavingFeedback() throws Exception {
        var storage=mock(FirebaseFeedbackStorage.class); var db=mock(CustomerFeedbackService.class);
        var uploaded=new FirebaseFeedbackStorage.Uploaded("feedback/test.png","https://example.invalid/test.png","image/png",4,2,2);
        when(storage.upload(any())).thenReturn(uploaded).thenThrow(new IllegalStateException("provider unavailable"));
        var submit=new FeedbackSubmission(new FeedbackImages(),storage,db);
        var file=new MockMultipartFile("images","test.png","image/png",png());
        assertThatThrownBy(()->submit.submit(null,List.of(file,file))).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(uploaded);verifyNoInteractions(db);
    }
    @Test void missingFirebaseConfigurationFailsClosed() throws Exception {
        var image=new FeedbackImages().validate(List.of(new MockMultipartFile("images","test.png","image/png",png()))).getFirst();
        assertThatThrownBy(()->new FirebaseFeedbackStorage("",false).upload(image)).isInstanceOf(IllegalStateException.class);
    }
}
