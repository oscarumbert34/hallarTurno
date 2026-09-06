package com.turnero.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.business.BusinessStatus;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.user.User;
import com.turnero.user.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerContactServiceTests {

    private final CustomerContactRepository customerContactRepository = org.mockito.Mockito.mock(CustomerContactRepository.class);
    private final BusinessRepository businessRepository = org.mockito.Mockito.mock(BusinessRepository.class);
    private final OwnershipGuard ownershipGuard = new OwnershipGuard();
    private final CustomerContactService service = new CustomerContactService(
            customerContactRepository,
            businessRepository,
            ownershipGuard
    );

    @Test
    void findByPhoneNormalizesPhoneAndRequiresBusinessOwner() {
        User owner = user("owner@example.com", UserRole.BUSINESS);
        Business business = business(owner);
        CustomerContact contact = contact(business, "Ana Cliente", "+54 11 5555-1234", "ana@example.com");
        AuthenticatedUser currentUser = new AuthenticatedUser(owner.getId(), owner.getEmail(), owner.getRoles());
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(customerContactRepository.findByBusinessIdAndNormalizedPhone(business.getId(), "541155551234"))
                .thenReturn(Optional.of(contact));

        CustomerContactResponse response = service.findByPhone(business.getId(), "+54 (11) 5555-1234", currentUser);

        assertThat(response.email()).isEqualTo("ana@example.com");
        verify(customerContactRepository).findByBusinessIdAndNormalizedPhone(business.getId(), "541155551234");
    }

    @Test
    void findByPhoneRejectsOtherBusinessUsers() {
        User owner = user("owner@example.com", UserRole.BUSINESS);
        User other = user("other@example.com", UserRole.BUSINESS);
        Business business = business(owner);
        AuthenticatedUser currentUser = new AuthenticatedUser(other.getId(), other.getEmail(), other.getRoles());
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThatThrownBy(() -> service.findByPhone(business.getId(), "+54 11 5555-1234", currentUser))
                .isInstanceOf(ApiException.class)
                .hasMessage("Customer contacts can only be viewed by the business owner or an admin");
    }

    @Test
    void findOrCreateForBookingCreatesContactWhenPhoneDoesNotExist() {
        User owner = user("owner@example.com", UserRole.BUSINESS);
        Business business = business(owner);
        when(customerContactRepository.findByBusinessIdAndNormalizedPhone(business.getId(), "541155551234"))
                .thenReturn(Optional.empty());
        when(customerContactRepository.saveAndFlush(any(CustomerContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerContact contact = service.findOrCreateForBooking(
                business,
                " Ana Cliente ",
                "+54 (11) 5555-1234",
                " ANA@example.COM "
        );

        ArgumentCaptor<CustomerContact> captor = ArgumentCaptor.forClass(CustomerContact.class);
        verify(customerContactRepository).saveAndFlush(captor.capture());
        assertThat(contact).isSameAs(captor.getValue());
        assertThat(contact.getName()).isEqualTo("Ana Cliente");
        assertThat(contact.getPhone()).isEqualTo("+54 (11) 5555-1234");
        assertThat(contact.getNormalizedPhone()).isEqualTo("541155551234");
        assertThat(contact.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void findOrCreateForBookingReusesAndUpdatesExistingContact() {
        User owner = user("owner@example.com", UserRole.BUSINESS);
        Business business = business(owner);
        CustomerContact contact = contact(business, "Ana", "+54 11 5555-1234", null);
        when(customerContactRepository.findByBusinessIdAndNormalizedPhone(business.getId(), "541155551234"))
                .thenReturn(Optional.of(contact));

        CustomerContact result = service.findOrCreateForBooking(
                business,
                "Ana Actualizada",
                "+54 11 5555-1234",
                "ana@example.com"
        );

        assertThat(result).isSameAs(contact);
        assertThat(result.getName()).isEqualTo("Ana Actualizada");
        assertThat(result.getEmail()).isEqualTo("ana@example.com");
    }

    private User user(String email, UserRole role) {
        User user = User.create(email, "hash", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Business business(User owner) {
        Business business = Business.create(
                owner,
                "Negocio",
                null,
                null,
                null,
                "negocio",
                BusinessStatus.ACTIVE
        );
        ReflectionTestUtils.setField(business, "id", UUID.randomUUID());
        return business;
    }

    private CustomerContact contact(Business business, String name, String phone, String email) {
        CustomerContact contact = CustomerContact.create(business, name, phone, email);
        ReflectionTestUtils.setField(contact, "id", UUID.randomUUID());
        return contact;
    }
}
