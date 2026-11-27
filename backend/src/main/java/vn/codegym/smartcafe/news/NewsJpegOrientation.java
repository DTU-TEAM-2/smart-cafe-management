package vn.codegym.smartcafe.news;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/** Apply the display orientation before re-encoding discards private EXIF metadata. */
final class NewsJpegOrientation {
    private NewsJpegOrientation() { }

    static BufferedImage apply(BufferedImage decoded, byte[] source) {
        int orientation = 1;
        try {
            // Only EXIF is needed; do not run unrelated XMP/IPTC/etc. metadata readers.
            var metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(source), List.of(new ExifReader()));
            var directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer value = directory == null ? null : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            if (value != null) orientation = value;
        } catch (JpegProcessingException | IOException ignored) {
            // Already decoded JPEG pixels remain usable; broken metadata is never retained.
        }
        if (orientation < 2 || orientation > 8) return decoded;
        int width = decoded.getWidth(), height = decoded.getHeight();
        var transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> throw new IllegalStateException("Unsupported JPEG orientation");
        };
        var normalized = new BufferedImage(orientation >= 5 ? height : width,
                orientation >= 5 ? width : height, BufferedImage.TYPE_INT_RGB);
        var graphics = normalized.createGraphics();
        try { graphics.drawImage(decoded, transform, null); }
        finally { graphics.dispose(); }
        return normalized;
    }
}
