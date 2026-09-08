package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.common.ApiException;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicBusinessPageServiceTests {

    private final BusinessRepository businessRepository = mock(BusinessRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final ServiceOfferingRepository serviceOfferingRepository = mock(ServiceOfferingRepository.class);
    private final PublicBusinessPageService service = new PublicBusinessPageService(
            businessRepository,
            branchRepository,
            serviceOfferingRepository
    );

    @Test
    void returnsOnlyPublicBusinessFieldsAndActiveBranches() {
        UUID businessId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Business business = business(businessId, "centro-piedica");
        Branch branch = branch(branchId, business, BranchStatus.ACTIVE);
        when(businessRepository.findBySlugAndStatus("centro-piedica", BusinessStatus.ACTIVE))
                .thenReturn(Optional.of(business));
        when(branchRepository.findByBusinessIdAndStatusOrderByNameAsc(businessId, BranchStatus.ACTIVE))
                .thenReturn(List.of(branch));

        PublicBusinessDetailResponse response = service.findBySlug("centro-piedica");

        assertThat(response.id()).isEqualTo(businessId);
        assertThat(response.email()).isEqualTo("contacto@centro.test");
        assertThat(response.branches()).singleElement().satisfies(publicBranch -> {
            assertThat(publicBranch.id()).isEqualTo(branchId);
            assertThat(publicBranch.city()).isEqualTo("Los Polvorines");
        });
        verify(branchRepository).findByBusinessIdAndStatusOrderByNameAsc(businessId, BranchStatus.ACTIVE);
    }

    @Test
    void missingOrInactiveBusinessIsReportedAsNotFound() {
        when(businessRepository.findBySlugAndStatus("oculto", BusinessStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySlug("oculto"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Business not found");
        verifyNoInteractions(branchRepository, serviceOfferingRepository);
    }

    @Test
    void returnsActiveGlobalAndBranchServicesForAnActiveOwnedBranch() {
        UUID businessId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Business business = business(businessId, "centro-piedica");
        Branch branch = branch(branchId, business, BranchStatus.ACTIVE);
        ServiceOffering offering = mock(ServiceOffering.class);
        when(offering.getId()).thenReturn(UUID.randomUUID());
        when(offering.getName()).thenReturn("Consulta podológica");
        when(offering.getDescription()).thenReturn("Evaluación completa");
        when(offering.getDurationMinutes()).thenReturn(30);
        when(offering.getPrice()).thenReturn(new BigDecimal("15000.00"));
        when(offering.getCurrency()).thenReturn("ARS");
        when(businessRepository.findBySlugAndStatus("centro-piedica", BusinessStatus.ACTIVE))
                .thenReturn(Optional.of(business));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(serviceOfferingRepository.findPublicActiveForBranch(
                businessId,
                branchId,
                ServiceOfferingStatus.ACTIVE
        )).thenReturn(List.of(offering));

        List<PublicBranchServiceResponse> response = service.findBranchServices("centro-piedica", branchId);

        assertThat(response).singleElement().satisfies(publicService -> {
            assertThat(publicService.name()).isEqualTo("Consulta podológica");
            assertThat(publicService.price()).isEqualByComparingTo("15000.00");
            assertThat(publicService.currency()).isEqualTo("ARS");
        });
    }

    private Business business(final UUID id, final String slug) {
        Business business = mock(Business.class);
        when(business.getId()).thenReturn(id);
        when(business.getName()).thenReturn("Centro Piedica");
        when(business.getSlug()).thenReturn(slug);
        when(business.getShortDescription()).thenReturn("Centro especializado");
        when(business.getPhone()).thenReturn("11 2345-6789");
        when(business.getContactEmail()).thenReturn("contacto@centro.test");
        return business;
    }

    private Branch branch(final UUID id, final Business business, final BranchStatus status) {
        Branch branch = mock(Branch.class);
        when(branch.getId()).thenReturn(id);
        when(branch.getBusiness()).thenReturn(business);
        when(branch.getStatus()).thenReturn(status);
        when(branch.getName()).thenReturn("Sucursal Los Polvorines");
        when(branch.getAddress()).thenReturn("Av. Presidente Perón 2456");
        when(branch.getLocality()).thenReturn("Los Polvorines");
        when(branch.getProvince()).thenReturn("Buenos Aires");
        return branch;
    }
}
