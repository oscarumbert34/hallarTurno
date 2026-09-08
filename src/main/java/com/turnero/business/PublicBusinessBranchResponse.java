package com.turnero.business;

import com.turnero.branch.Branch;
import java.util.UUID;

public record PublicBusinessBranchResponse(
        UUID id,
        String name,
        String address,
        String city,
        String province
) {

    static PublicBusinessBranchResponse from(final Branch branch) {
        return new PublicBusinessBranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getLocality(),
                branch.getProvince()
        );
    }
}
