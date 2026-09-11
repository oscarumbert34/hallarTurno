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
@RequestMapping("/api/v1/branches/{branchId}/schedule-exceptions")
public class BranchScheduleExceptionController {
    private final BranchScheduleExceptionService service;

    public BranchScheduleExceptionController(BranchScheduleExceptionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BranchScheduleExceptionResponse> create(
            @PathVariable UUID branchId, @Valid @RequestBody BranchScheduleExceptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        BranchScheduleExceptionResponse response = service.create(branchId, request, currentUser);
        return ResponseEntity.created(URI.create("/api/v1/branches/" + branchId
                + "/schedule-exceptions/" + response.id())).body(response);
    }

    @GetMapping
    List<BranchScheduleExceptionResponse> findAll(
            @PathVariable UUID branchId, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.findAll(branchId, currentUser);
    }

    @PutMapping("/{id}")
    BranchScheduleExceptionResponse update(
            @PathVariable UUID branchId, @PathVariable UUID id,
            @Valid @RequestBody BranchScheduleExceptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.update(branchId, id, request, currentUser);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable UUID branchId, @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        service.delete(branchId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
