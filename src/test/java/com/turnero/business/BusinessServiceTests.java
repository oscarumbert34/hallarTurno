package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessServiceTests {

    private final BusinessRepository businessRepository = org.mockito.Mockito.mock(BusinessRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final BusinessProperties properties = new BusinessProperties();
    private final SlugGenerator slugGenerator = new SlugGenerator();
    private final OwnershipGuard ownershipGuard = new OwnershipGuard();
    private final BusinessService businessService = new BusinessService(
            businessRepository,
            userRepository,
            properties,
            slugGenerator,
            ownershipGuard
    );

    @Test
    void createGeneratesStableUniqueSlugAndUsesAuthenticatedOwner() {
        UUID ownerId = UUID.randomUUID();
        User owner = user(ownerId, UserRole.BUSINESS);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(businessRepository.existsBySlug("cafe-central")).thenReturn(true);
        when(businessRepository.existsBySlug("cafe-central-2")).thenReturn(false);
        when(businessRepository.saveAndFlush(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BusinessResponse response = businessService.create(
                new BusinessRequest(" Cafe Central ", " Turnos por la tarde ", " 123 ", " INFO@example.com "),
                new AuthenticatedUser(ownerId, "owner@example.com", Set.of(UserRole.BUSINESS))
        );

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOwner().getId()).isEqualTo(ownerId);
        assertThat(response.slug()).isEqualTo("cafe-central-2");
        assertThat(response.name()).isEqualTo("Cafe Central");
        assertThat(response.status()).isEqualTo(BusinessStatus.ACTIVE);
    }

    @Test
    void createRejectsCustomerRole() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> businessService.create(
                new BusinessRequest("Cafe Central", null, null, null),
                new AuthenticatedUser(customerId, "customer@example.com", Set.of(UserRole.CUSTOMER))
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only business users or admins can manage businesses");
    }

    @Test
    void updateRejectsUsersThatAreNotOwnerOrAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Business business = business(UUID.randomUUID(), user(ownerId, UserRole.BUSINESS));
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThatThrownBy(() -> businessService.update(
                business.getId(),
                new BusinessRequest("Nuevo nombre", null, null, null),
                new AuthenticatedUser(otherUserId, "other@example.com", Set.of(UserRole.BUSINESS))
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Business can only be managed by its owner or an admin");
    }

    @Test
    void adminCanUpdateAnyBusiness() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Business business = business(UUID.randomUUID(), user(ownerId, UserRole.BUSINESS));
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        BusinessResponse response = businessService.update(
                business.getId(),
                new BusinessRequest("Nombre admin", null, null, null),
                new AuthenticatedUser(adminId, "admin@example.com", Set.of(UserRole.ADMIN))
        );

        assertThat(response.name()).isEqualTo("Nombre admin");
    }

    private User user(UUID id, UserRole role) {
        User user = User.create(role.name().toLowerCase() + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Business business(UUID id, User owner) {
        Business business = Business.create(owner, "Nombre", null, null, null, "nombre", BusinessStatus.ACTIVE);
        ReflectionTestUtils.setField(business, "id", id);
        return business;
    }
}
