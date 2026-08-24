package com.turnero.marketplace.dto;

import com.turnero.branch.Branch;
import com.turnero.service.ServiceOffering;

import java.util.List;

public record ServiceSlots(
        ServiceOffering offering,
        Branch branch,
        List<PublicAvailabilitySlotResponse> slots
) {
}
