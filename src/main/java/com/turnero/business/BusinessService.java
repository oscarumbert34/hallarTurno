package com.turnero.business;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessProperties properties;
    private final SlugGenerator slugGenerator;
    private final OwnershipGuard ownershipGuard;

    public BusinessService(
            final BusinessRepository businessRepository,
            final UserRepository userRepository,
            final BusinessProperties properties,
            final SlugGenerator slugGenerator,
            final OwnershipGuard ownershipGuard
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.slugGenerator = slugGenerator;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public BusinessResponse create(final BusinessRequest request, final AuthenticatedUser currentUser) {
        this.ownershipGuard.requireBusinessOrAdmin(currentUser);
        final User owner = this.userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        final String slug = this.slugGenerator.uniqueSlug(request.name(), this.businessRepository::existsBySlug);
        final Business business = Business.create(
                owner,
                request.name().trim(),
                this.blankToNull(request.shortDescription()),
                this.blankToNull(request.phone()),
                this.blankToNull(request.contactEmail()),
                slug,
                this.properties.getInitialStatus()
        );
        return BusinessResponse.from(this.businessRepository.saveAndFlush(business));
    }

    @Transactional(readOnly = true)
    public List<PublicBusinessResponse> findPublic() {
        return this.businessRepository.findByStatusOrderByNameAsc(BusinessStatus.ACTIVE).stream()
                .map(PublicBusinessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> findOwned(final AuthenticatedUser currentUser) {
        //ownershipGuard.requireBusinessOrAdmin(currentUser);
        return this.businessRepository.findByOwnerIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(BusinessResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponse get(final UUID id, final AuthenticatedUser currentUser) {
        final Business business = this.findBusiness(id);
        this.ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Business can only be managed by its owner or an admin");
        return BusinessResponse.from(business);
    }

    @Transactional
    public BusinessResponse update(final UUID id, final BusinessRequest request, final AuthenticatedUser currentUser) {
        final Business business = this.findBusiness(id);
        this.ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Business can only be managed by its owner or an admin");
        business.updateDetails(
                request.name().trim(),
                this.blankToNull(request.shortDescription()),
                this.blankToNull(request.phone()),
                this.blankToNull(request.contactEmail())
        );
        return BusinessResponse.from(business);
    }

    @Transactional
    public void delete(final UUID id, final AuthenticatedUser currentUser) {
        final Business business = this.findBusiness(id);
        this.ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Business can only be managed by its owner or an admin");
        this.businessRepository.delete(business);
    }

    private Business findBusiness(final UUID id) {
        return this.businessRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
