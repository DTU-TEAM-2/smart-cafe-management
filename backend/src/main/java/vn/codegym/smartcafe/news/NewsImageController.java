package vn.codegym.smartcafe.news;

import java.net.URI;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/news/images")
class NewsImageController {
    private final NewsImageService images;

    NewsImageController(NewsImageService images) { this.images = images; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<NewsImageService.UploadedImage> upload(@RequestPart("file") MultipartFile file) {
        var image = images.upload(file);
        return ResponseEntity.created(URI.create(image.url())).body(image);
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> read(@PathVariable long id, Principal principal) {
        var image = images.read(id, principal.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mimeType())).body(image.bytes());
    }
}
