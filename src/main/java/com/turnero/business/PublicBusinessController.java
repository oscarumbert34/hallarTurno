package com.turnero.business;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/businesses")
public class PublicBusinessController {

    private final PublicBusinessPageService publicBusinessPageService;

    public PublicBusinessController(final PublicBusinessPageService publicBusinessPageService) {
        this.publicBusinessPageService = publicBusinessPageService;
    }

    @GetMapping("/{slug}")
    public PublicBusinessDetailResponse getBySlug(@PathVariable final String slug) {
        return this.publicBusinessPageService.findBySlug(slug);
    }

    @GetMapping("/{slug}/branches/{branchId}/services")
    public List<PublicBranchServiceResponse> getBranchServices(
            @PathVariable final String slug,
            @PathVariable final UUID branchId
    ) {
        return this.publicBusinessPageService.findBranchServices(slug, branchId);
    }
}
