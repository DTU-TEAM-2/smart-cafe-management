package vn.codegym.smartcafe.customerfeedback;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

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
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    @Test void validatesActualBytesRatherThanTrustingExtensionOrMime() throws Exception {
        var images = new FeedbackImages();
        var result = images.validate(List.of(new MockMultipartFile("images", "not-trusted.exe", "text/plain", png())));
        assertThat(result.getFirst().contentType()).isEqualTo("image/png");
        assertThat(result.getFirst().width()).isEqualTo(2);
        assertThatThrownBy(() -> images.validate(List.of(new MockMultipartFile("images", "fake.png", "image/png", "not-an-image".getBytes()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsCountEmptyAndOversizedFilesBeforeProviderCalls() throws Exception {
        var validator = new FeedbackImages();
        var valid = new MockMultipartFile("images", "test.png", "image/png", png());
        assertThatThrownBy(() -> validator.validate(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of(valid, valid, valid, valid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of(new MockMultipartFile("images", new byte[0])))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(List.of(new MockMultipartFile("images", new byte[5 * 1024 * 1024 + 1])))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void removesEarlierUploadsIfLaterUploadFailsWithoutSavingFeedback() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);
        var uploaded = new FirebaseFeedbackStorage.Uploaded("feedback/test.png", "https://example.invalid/test.png", "image/png", 4, 2, 2);
        when(storage.upload(any())).thenReturn(uploaded).thenThrow(new IllegalStateException("provider unavailable"));
        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", "Nội dung hợp lệ", BigDecimal.valueOf(5));
        assertThatThrownBy(() -> submit.submit(command, List.of(file, file))).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(uploaded);
        verifyNoInteractions(db);
        verifyNoInteractions(txManager);
    }

    @Test void missingFirebaseConfigurationFailsClosed() throws Exception {
        var image = new FeedbackImages().validate(List.of(new MockMultipartFile("images", "test.png", "image/png", png()))).getFirst();
        assertThatThrownBy(() -> new FirebaseFeedbackStorage("", false).upload(image)).isInstanceOf(IllegalStateException.class);
    }

    @Test void oversizedContentRejectedBeforeAnyStorageUpload() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);
        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        String hugeContent = "A".repeat(65_536);
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", hugeContent, BigDecimal.valueOf(5));

        assertThatThrownBy(() -> submit.submit(command, List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá dung lượng");

        verifyNoInteractions(storage);
        verifyNoInteractions(db);
        verifyNoInteractions(txManager);
    }

    @Test void retainsBlobsForReconciliationWhenCommitOutcomeIsUncertain() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);
        var txStatus = mock(TransactionStatus.class);

        when(txManager.getTransaction(any())).thenReturn(txStatus);
        when(db.submitWithImages(any(), any())).thenReturn("42");
        doThrow(new TransactionSystemException("Lost commit ACK")).when(txManager).commit(txStatus);

        var uploaded = new FirebaseFeedbackStorage.Uploaded("feedback/test.png", "https://example.invalid/test.png", "image/png", 4, 2, 2);
        when(storage.upload(any())).thenReturn(uploaded);

        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", "Nội dung phản hồi", BigDecimal.valueOf(5));

        assertThatThrownBy(() -> submit.submit(command, List.of(file)))
                .isInstanceOf(TransactionException.class);

        verify(txManager).commit(txStatus);
        verify(storage, never()).delete(any());
    }

    @Test void deletesBlobsWhenDatabaseExecutionFailsWithConfirmedRollback() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);
        var txStatus = mock(TransactionStatus.class);

        when(txManager.getTransaction(any())).thenReturn(txStatus);
        when(db.submitWithImages(any(), any())).thenThrow(new DataIntegrityViolationException("Constraint violation"));

        var uploaded = new FirebaseFeedbackStorage.Uploaded("feedback/test.png", "https://example.invalid/test.png", "image/png", 4, 2, 2);
        when(storage.upload(any())).thenReturn(uploaded);

        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", "Nội dung phản hồi", BigDecimal.valueOf(5));

        assertThatThrownBy(() -> submit.submit(command, List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(txManager).rollback(txStatus);
        verify(storage).delete(uploaded);
    }

    @Test void cleansUpBlobsWhenTransactionAcquisitionFails() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);

        when(txManager.getTransaction(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("DB pool timeout"));

        var uploaded = new FirebaseFeedbackStorage.Uploaded("feedback/test.png", "https://example.invalid/test.png", "image/png", 4, 2, 2);
        when(storage.upload(any())).thenReturn(uploaded);

        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", "Nội dung phản hồi", BigDecimal.valueOf(5));

        assertThatThrownBy(() -> submit.submit(command, List.of(file)))
                .isInstanceOf(org.springframework.transaction.CannotCreateTransactionException.class);

        verify(storage).delete(uploaded);
        verifyNoInteractions(db);
    }

    @Test void retainsBlobsForReconciliationWhenRollbackFails() throws Exception {
        var storage = mock(FirebaseFeedbackStorage.class);
        var db = mock(CustomerFeedbackService.class);
        var txManager = mock(PlatformTransactionManager.class);
        var txStatus = mock(TransactionStatus.class);

        when(txManager.getTransaction(any())).thenReturn(txStatus);
        when(db.submitWithImages(any(), any())).thenThrow(new DataIntegrityViolationException("DB error"));
        doThrow(new TransactionSystemException("Rollback ACK timeout")).when(txManager).rollback(txStatus);

        var uploaded = new FirebaseFeedbackStorage.Uploaded("feedback/test.png", "https://example.invalid/test.png", "image/png", 4, 2, 2);
        when(storage.upload(any())).thenReturn(uploaded);

        var submit = new FeedbackSubmission(new FeedbackImages(), storage, db, txManager);
        var file = new MockMultipartFile("images", "test.png", "image/png", png());
        var command = new CustomerFeedbackController.Command("Khách test", "test@example.invalid", "Nội dung phản hồi", BigDecimal.valueOf(5));

        assertThatThrownBy(() -> submit.submit(command, List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(txManager).rollback(txStatus);
        verify(storage, never()).delete(any());
    }

    @Test void goldenImageExifOrientationsAll8Supported() throws Exception {
        var images = new FeedbackImages();
        for (int orientation = 1; orientation <= 8; orientation++) {
            byte[] jpeg = createJpegWithExifGpsAndOrientation(4, 2, orientation);
            var file = new MockMultipartFile("images", "orient-" + orientation + ".jpg", "image/jpeg", jpeg);
            var result = images.validate(List.of(file)).getFirst();

            boolean transposed = orientation >= 5;
            int expectedWidth = transposed ? 2 : 4;
            int expectedHeight = transposed ? 4 : 2;
            assertThat(result.width()).as("Width for orientation " + orientation).isEqualTo(expectedWidth);
            assertThat(result.height()).as("Height for orientation " + orientation).isEqualTo(expectedHeight);

            var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
            assertThat(decoded).as("Decoded image for orientation " + orientation).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(expectedWidth);
            assertThat(decoded.getHeight()).isEqualTo(expectedHeight);

            assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");
        }
    }

    @Test void syntheticGpsMetadataIsStrippedAndExifOrientationIsApplied() throws Exception {
        byte[] jpegWithGps = createJpegWithExifGpsAndOrientation(4, 2, 6);
        assertThat(new String(jpegWithGps, StandardCharsets.ISO_8859_1)).contains("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");

        var file = new MockMultipartFile("images", "photo.jpg", "image/jpeg", jpegWithGps);
        var images = new FeedbackImages();
        var validatedList = images.validate(List.of(file));

        assertThat(validatedList).hasSize(1);
        var validated = validatedList.getFirst();
        // Original was 4x2 with Orientation 6 (Rotate 90 CW) -> Transformed to 2x4
        assertThat(validated.width()).isEqualTo(2);
        assertThat(validated.height()).isEqualTo(4);
        assertThat(validated.contentType()).isEqualTo("image/jpeg");

        // EXIF GPS metadata must be completely stripped from output bytes
        byte[] cleanBytes = validated.bytes();
        assertThat(new String(cleanBytes, StandardCharsets.ISO_8859_1)).doesNotContain("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");
        // Verify output bytes are valid JPEG raster readable by ImageIO
        var decoded = ImageIO.read(new ByteArrayInputStream(cleanBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(2);
        assertThat(decoded.getHeight()).isEqualTo(4);
    }

    private static byte[] createJpegWithExifGpsAndOrientation(int width, int height, int orientation) throws Exception {
        var baseOut = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", baseOut);
        byte[] baseJpeg = baseOut.toByteArray();

        // Construct synthetic APP1 segment containing EXIF with orientation and GPS marker
        var app1 = new ByteArrayOutputStream();
        app1.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        // TIFF header (Little Endian)
        app1.write(new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});
        // IFD0: 2 entries (Orientation 0x0112 and GPS IFD Pointer 0x8825)
        app1.write(new byte[]{0x02, 0x00});
        // Entry 1: Orientation (0x0112), type SHORT (3), count 1, value (orientation)
        app1.write(new byte[]{0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) orientation, 0x00, 0x00, 0x00});
        // Entry 2: GPSInfo pointer (0x8825), type LONG (4), count 1, offset to GPS IFD
        int gpsIfdOffset = 8 + 2 + 12 * 2 + 4; // offset 38 from TIFF header
        app1.write(new byte[]{0x25, (byte) 0x88, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) gpsIfdOffset, 0x00, 0x00, 0x00});
        // Next IFD offset: 0
        app1.write(new byte[]{0x00, 0x00, 0x00, 0x00});

        // GPS IFD: 1 entry containing synthetic GPS payload
        app1.write(new byte[]{0x01, 0x00});
        byte[] gpsData = "GPS_SYNTHETIC_DATA_LAT_10_LONG_106".getBytes(StandardCharsets.ISO_8859_1);
        int gpsDataOffset = gpsIfdOffset + 2 + 12 + 4;
        app1.write(new byte[]{0x1D, 0x00, 0x07, 0x00, (byte) gpsData.length, 0x00, 0x00, 0x00, (byte) gpsDataOffset, 0x00, 0x00, 0x00});
        app1.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        app1.write(gpsData);

        byte[] app1Payload = app1.toByteArray();
        int app1Length = app1Payload.length + 2;

        var result = new ByteArrayOutputStream();
        // SOI marker 0xFFD8
        result.write(baseJpeg[0]);
        result.write(baseJpeg[1]);
        // Insert synthetic APP1 marker
        result.write(0xFF);
        result.write(0xE1);
        result.write((app1Length >> 8) & 0xFF);
        result.write(app1Length & 0xFF);
        result.write(app1Payload);
        // Append rest of the JPEG
        result.write(baseJpeg, 2, baseJpeg.length - 2);
        return result.toByteArray();
    }
}
