package com.turnero.branch;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @PostMapping("/businesses/{businessId}/branches")
    ResponseEntity<BranchResponse> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        BranchResponse response = branchService.create(businessId, request, currentUser);
        return ResponseEntity.created(URI.create("/api/v1/branches/" + response.id())).body(response);
    }

    @GetMapping("/businesses/{businessId}/branches")
    List<BranchResponse> findByBusiness(
            @PathVariable UUID businessId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return branchService.findByBusiness(businessId, currentUser);
    }

    @GetMapping("/branches/{id}")
    BranchResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return branchService.get(id, currentUser);
    }

    @PutMapping("/branches/{id}")
    BranchResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return branchService.update(id, request, currentUser);
    }

    @DeleteMapping("/branches/{id}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        branchService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
