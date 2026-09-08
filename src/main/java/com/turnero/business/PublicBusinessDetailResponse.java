package com.turnero.business;

import java.util.List;
import java.util.UUID;

public record PublicBusinessDetailResponse(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        String phone,
        String email,
        List<PublicBusinessBranchResponse> branches
) {
}
