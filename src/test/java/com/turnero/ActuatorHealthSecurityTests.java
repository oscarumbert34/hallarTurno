package com.turnero;

import com.turnero.auth.AuthService;
import com.turnero.availability.AvailabilityService;
import com.turnero.booking.BookingReminderService;
import com.turnero.booking.BookingService;
import com.turnero.branch.BranchService;
import com.turnero.branch.BranchScheduleExceptionService;
import com.turnero.business.BusinessService;
import com.turnero.business.PublicBusinessPageService;
import com.turnero.customer.CustomerContactService;
import com.turnero.employee.BookableResourceService;
import com.turnero.marketplace.PublicAvailabilityService;
import com.turnero.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "management.endpoints.web.exposure.include=health,info,metrics",
        "management.endpoint.health.probes.enabled=true",
        "management.info.env.enabled=true"
})
@AutoConfigureMockMvc
class ActuatorHealthSecurityTests {

    @MockBean
    private AuthService authService;

    @MockBean
    private BusinessService businessService;

    @MockBean
    private PublicBusinessPageService publicBusinessPageService;

    @MockBean
    private BranchService branchService;

    @MockBean
    private BranchScheduleExceptionService branchScheduleExceptionService;

    @MockBean
    private ServiceOfferingService serviceOfferingService;

    @MockBean
    private BookableResourceService bookableResourceService;

    @MockBean
    private AvailabilityService availabilityService;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private BookingReminderService bookingReminderService;

    @MockBean
    private PublicAvailabilityService publicAvailabilityService;

    @MockBean
    private CustomerContactService customerContactService;

    private final MockMvc mockMvc;

    @Autowired
    ActuatorHealthSecurityTests(final MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        this.mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void readinessAndLivenessEndpointsArePublic() throws Exception {
        this.mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void infoAndMetricsEndpointsArePublic() throws Exception {
        this.mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    void nonHealthEndpointsRequireAuthentication() throws Exception {
        this.mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hallarturnoLogoEndpointIsPublic() throws Exception {
        this.mockMvc.perform(get("/api/v1/public/assets/logo-hallarturno.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"))
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")));
    }

    @Test
    void testingBookingReminderEndpointRequiresBusinessOrAdmin() throws Exception {
        this.mockMvc.perform(post("/api/v1/testing/booking-reminders/run"))
                .andExpect(status().isUnauthorized());

        this.mockMvc.perform(post("/api/v1/testing/booking-reminders/run")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only business users or admins can run testing reminders"));
        verifyNoInteractions(this.bookingReminderService);

        this.mockMvc.perform(post("/api/v1/testing/booking-reminders/run")
                        .with(user("owner").roles("BUSINESS")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.triggered").value(true));
        verify(this.bookingReminderService).sendDueReminders();
    }
}
