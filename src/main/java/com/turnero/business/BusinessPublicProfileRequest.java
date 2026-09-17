package com.turnero.business;

import jakarta.validation.constraints.Size;

public record BusinessPublicProfileRequest(
        @Size(max = 500) String publicDescription,
        @Size(max = 5000) String aboutUs,
        @Size(max = 40) String whatsapp,
        @Size(max = 255) String instagram
) {
}
