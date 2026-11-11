package vn.codegym.smartcafe.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomeHighlightsController {
    private final HomeHighlightsService service;

    HomeHighlightsController(HomeHighlightsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/home/highlights")
    HomeHighlights highlights() {
        return service.getHighlights();
    }

}
