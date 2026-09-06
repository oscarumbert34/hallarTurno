package com.turnero;

import com.turnero.auth.AuthService;
import com.turnero.availability.AvailabilityService;
import com.turnero.booking.BookingService;
import com.turnero.branch.BranchService;
import com.turnero.business.BusinessService;
import com.turnero.customer.CustomerContactService;
import com.turnero.employee.BookableResourceService;
import com.turnero.marketplace.PublicAvailabilityService;
import com.turnero.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class TurneroApplicationTests {

    @MockBean
    private AuthService authService;

    @MockBean
    private BusinessService businessService;

    @MockBean
    private BranchService branchService;

    @MockBean
    private ServiceOfferingService serviceOfferingService;

    @MockBean
    private BookableResourceService bookableResourceService;

    @MockBean
    private AvailabilityService availabilityService;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private PublicAvailabilityService publicAvailabilityService;

    @MockBean
    private CustomerContactService customerContactService;

    @Test
    void contextLoads() {
    }
}
