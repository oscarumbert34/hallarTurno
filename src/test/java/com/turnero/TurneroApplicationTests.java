package com.turnero;

import com.turnero.auth.AuthService;
import com.turnero.availability.AvailabilityService;
import com.turnero.branch.BranchService;
import com.turnero.booking.BookingService;
import com.turnero.business.BusinessService;
import com.turnero.employee.BookableResourceService;
import com.turnero.marketplace.PublicAvailabilityService;
import com.turnero.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Test
    void contextLoads() {
    }
}
