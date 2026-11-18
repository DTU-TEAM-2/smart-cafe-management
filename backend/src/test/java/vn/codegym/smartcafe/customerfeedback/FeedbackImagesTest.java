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

        assertThatThrownBy(() -> submit.submit(command, List.of(file, file)))
                .isInstanceOf(org.springframework.transaction.CannotCreateTransactionException.class);

        verify(storage, times(2)).delete(uploaded);
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
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(ex.getSuppressed()).anyMatch(s -> s.getMessage().contains("Rollback ACK timeout")));

        verify(txManager).rollback(txStatus);
        verify(storage, never()).delete(any());
    }

    enum CornerColor { RED, GREEN, BLUE, MAGENTA }

    private static CornerColor getCornerColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r > 150 && g < 100 && b < 100) return CornerColor.RED;
        if (g > 150 && r < 100 && b < 100) return CornerColor.GREEN;
        if (b > 150 && r < 100 && g < 100) return CornerColor.BLUE;
        if (r > 150 && b > 150 && g < 100) return CornerColor.MAGENTA;
        throw new AssertionError("Unknown corner color: r=" + r + ", g=" + g + ", b=" + b);
    }

    @Test void goldenImageExifOrientationsAll8Supported() throws Exception {
        var images = new FeedbackImages();
        for (int orientation = 1; orientation <= 8; orientation++) {
            // Asymmetric image 32x16 with 4 distinctive corner markers: TL=RED, TR=GREEN, BL=BLUE, BR=MAGENTA
            byte[] jpeg = createJpegWithExifGpsAndOrientation(32, 16, orientation, false);
            var file = new MockMultipartFile("images", "orient-" + orientation + ".jpg", "image/jpeg", jpeg);
            var result = images.validate(List.of(file)).getFirst();

            boolean transposed = orientation >= 5;
            int expectedWidth = transposed ? 16 : 32;
            int expectedHeight = transposed ? 32 : 16;
            assertThat(result.width()).as("Width for orientation " + orientation).isEqualTo(expectedWidth);
            assertThat(result.height()).as("Height for orientation " + orientation).isEqualTo(expectedHeight);

            var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
            assertThat(decoded).as("Decoded image for orientation " + orientation).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(expectedWidth);
            assertThat(decoded.getHeight()).isEqualTo(expectedHeight);

            // Verify directional placement of distinctive corner markers
            CornerColor expectedTL = switch (orientation) {
                case 1, 5 -> CornerColor.RED;
                case 2, 8 -> CornerColor.GREEN;
                case 3, 7 -> CornerColor.MAGENTA;
                case 4, 6 -> CornerColor.BLUE;
                default -> throw new IllegalStateException();
            };
            CornerColor expectedTR = switch (orientation) {
                case 1, 7 -> CornerColor.GREEN;
                case 2, 6 -> CornerColor.RED;
                case 3, 5 -> CornerColor.BLUE;
                case 4, 8 -> CornerColor.MAGENTA;
                default -> throw new IllegalStateException();
            };
            CornerColor expectedBL = switch (orientation) {
                case 1, 7 -> CornerColor.BLUE;
                case 2, 6 -> CornerColor.MAGENTA;
                case 3, 5 -> CornerColor.GREEN;
                case 4, 8 -> CornerColor.RED;
                default -> throw new IllegalStateException();
            };
            CornerColor expectedBR = switch (orientation) {
                case 1, 5 -> CornerColor.MAGENTA;
                case 2, 8 -> CornerColor.BLUE;
                case 3, 7 -> CornerColor.RED;
                case 4, 6 -> CornerColor.GREEN;
                default -> throw new IllegalStateException();
            };

            int w = decoded.getWidth();
            int h = decoded.getHeight();
            assertThat(getCornerColor(decoded.getRGB(1, 1))).as("TL for orientation " + orientation).isEqualTo(expectedTL);
            assertThat(getCornerColor(decoded.getRGB(w - 2, 1))).as("TR for orientation " + orientation).isEqualTo(expectedTR);
            assertThat(getCornerColor(decoded.getRGB(1, h - 2))).as("BL for orientation " + orientation).isEqualTo(expectedBL);
            assertThat(getCornerColor(decoded.getRGB(w - 2, h - 2))).as("BR for orientation " + orientation).isEqualTo(expectedBR);

            assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");
        }
    }

    @Test void jpegWithExifFillByteProperlyDetectsOrientationAndStripsExif() throws Exception {
        // Construct ImageIO-decodable JPEG with extra FF marker fill byte (FF FF E1 ...) and Orientation 6
        byte[] jpegWithFill = createJpegWithExifGpsAndOrientation(32, 16, 6, true);
        assertThat(new String(jpegWithFill, StandardCharsets.ISO_8859_1)).contains("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");

        // Verify ImageIO can read the raw JPEG without error
        var rawDecoded = ImageIO.read(new ByteArrayInputStream(jpegWithFill));
        assertThat(rawDecoded).isNotNull();
        assertThat(rawDecoded.getWidth()).isEqualTo(32);
        assertThat(rawDecoded.getHeight()).isEqualTo(16);

        // Validate via FeedbackImages
        var file = new MockMultipartFile("images", "fill-test.jpg", "image/jpeg", jpegWithFill);
        var images = new FeedbackImages();
        var validatedList = images.validate(List.of(file));

        assertThat(validatedList).hasSize(1);
        var validated = validatedList.getFirst();
        // Orientation 6: 32x16 rotated 90 CW -> 16x32
        assertThat(validated.width()).isEqualTo(16);
        assertThat(validated.height()).isEqualTo(32);
        assertThat(validated.contentType()).isEqualTo("image/jpeg");

        // Decoded transformed image verification: dimensions and corner placement for Orientation 6
        var decoded = ImageIO.read(new ByteArrayInputStream(validated.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(16);
        assertThat(decoded.getHeight()).isEqualTo(32);
        assertThat(getCornerColor(decoded.getRGB(1, 1))).isEqualTo(CornerColor.BLUE);
        assertThat(getCornerColor(decoded.getRGB(14, 1))).isEqualTo(CornerColor.RED);
        assertThat(getCornerColor(decoded.getRGB(1, 30))).isEqualTo(CornerColor.MAGENTA);
        assertThat(getCornerColor(decoded.getRGB(14, 30))).isEqualTo(CornerColor.GREEN);

        // EXIF/GPS must be stripped
        assertThat(new String(validated.bytes(), StandardCharsets.ISO_8859_1))
                .doesNotContain("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");
    }

    @Test void syntheticGpsMetadataIsStrippedAndExifOrientationIsApplied() throws Exception {
        byte[] jpegWithGps = createJpegWithExifGpsAndOrientation(32, 16, 6, false);
        assertThat(new String(jpegWithGps, StandardCharsets.ISO_8859_1)).contains("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");

        var file = new MockMultipartFile("images", "photo.jpg", "image/jpeg", jpegWithGps);
        var images = new FeedbackImages();
        var validatedList = images.validate(List.of(file));

        assertThat(validatedList).hasSize(1);
        var validated = validatedList.getFirst();
        // Original was 32x16 with Orientation 6 (Rotate 90 CW) -> Transformed to 16x32
        assertThat(validated.width()).isEqualTo(16);
        assertThat(validated.height()).isEqualTo(32);
        assertThat(validated.contentType()).isEqualTo("image/jpeg");

        // EXIF GPS metadata must be completely stripped from output bytes
        byte[] cleanBytes = validated.bytes();
        assertThat(new String(cleanBytes, StandardCharsets.ISO_8859_1)).doesNotContain("GPS_SYNTHETIC_DATA_LAT_10_LONG_106");
        // Verify output bytes are valid JPEG raster readable by ImageIO
        var decoded = ImageIO.read(new ByteArrayInputStream(cleanBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(16);
        assertThat(decoded.getHeight()).isEqualTo(32);
    }

    private static byte[] createJpegWithExifGpsAndOrientation(int width, int height, int orientation, boolean extraFillByte) throws Exception {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, width, height);

            // 4 distinctive corner markers (size 4x4)
            // Top-Left: RED
            g.setColor(new java.awt.Color(255, 0, 0));
            g.fillRect(0, 0, 4, 4);
            // Top-Right: GREEN
            g.setColor(new java.awt.Color(0, 255, 0));
            g.fillRect(width - 4, 0, 4, 4);
            // Bottom-Left: BLUE
            g.setColor(new java.awt.Color(0, 0, 255));
            g.fillRect(0, height - 4, 4, 4);
            // Bottom-Right: MAGENTA
            g.setColor(new java.awt.Color(255, 0, 255));
            g.fillRect(width - 4, height - 4, 4, 4);

            // Asymmetric arrow pointing towards top-right
            g.setColor(java.awt.Color.BLACK);
            g.drawLine(4, height - 5, width - 5, 4);
            g.drawLine(width - 5, 4, width - 5, Math.min(height - 1, 8));
            g.drawLine(width - 5, 4, Math.max(0, width - 9), 4);
        } finally {
            g.dispose();
        }

        var baseOut = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baseOut);
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

        // Insert synthetic APP1 marker (optionally with 0xFF fill byte)
        result.write(0xFF);
        if (extraFillByte) {
            result.write(0xFF);
        }
        result.write(0xE1);
        result.write((app1Length >> 8) & 0xFF);
        result.write(app1Length & 0xFF);
        result.write(app1Payload);

        // Append rest of the JPEG
        result.write(baseJpeg, 2, baseJpeg.length - 2);
        return result.toByteArray();
    }
}
