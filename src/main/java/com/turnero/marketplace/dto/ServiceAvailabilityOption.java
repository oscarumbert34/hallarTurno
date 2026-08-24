package com.turnero.marketplace.dto;

import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.service.ServiceOffering;

import java.util.List;

public record ServiceAvailabilityOption(
        ServiceOffering offering,
        Branch branch,
        List<AvailabilitySlotResponse> slots
) {
}
