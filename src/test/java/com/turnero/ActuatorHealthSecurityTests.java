package com.turnero;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.turnero.auth.AuthService;
import com.turnero.availability.AvailabilityService;
import com.turnero.branch.BranchService;
import com.turnero.booking.BookingService;
import com.turnero.business.BusinessService;
import com.turnero.employee.BookableResourceService;
import com.turnero.marketplace.PublicAvailabilityService;
import com.turnero.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
@AutoConfigureMockMvc
class ActuatorHealthSecurityTests {

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

    private final MockMvc mockMvc;

    @Autowired
    ActuatorHealthSecurityTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void nonHealthEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
    }
}
