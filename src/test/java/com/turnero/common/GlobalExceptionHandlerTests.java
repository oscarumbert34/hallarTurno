package com.turnero.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GlobalExceptionHandlerTests {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(meterRegistry);

    @Test
    void unexpectedErrorReturnsSafeBodyWithRequestIdAndCountsError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "test-request-id");

        var response = handler.handleException(new IllegalStateException("internal failure details"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requestId()).isEqualTo("test-request-id");
        assertThat(response.getBody().message()).isEqualTo("Unexpected error");
        assertThat(response.getBody().details()).isEmpty();
        assertThat(meterRegistry.counter("turnero.http.errors", "status", "500").count()).isEqualTo(1);
    }

    @Test
    void apiExceptionKeepsControlledMessageAndRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "controlled-request-id");

        var response = handler.handleApiException(
                new ApiException(HttpStatus.CONFLICT, "Booking slot is not available"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requestId()).isEqualTo("controlled-request-id");
        assertThat(response.getBody().message()).isEqualTo("Booking slot is not available");
    }

    @Test
    void requestIdFilterPropagatesIncomingRequestId() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.HEADER_NAME, "front-request-123");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("front-request-123");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)).isEqualTo("front-request-123");
    }
}
