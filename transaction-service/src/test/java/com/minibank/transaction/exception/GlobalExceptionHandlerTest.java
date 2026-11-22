package com.minibank.transaction.exception;

import com.minibank.transaction.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/reconciliation/daily");

    @Test
    void databaseErrorsDoNotLeakSql() {
        var ex = new InvalidDataAccessResourceUsageException(
                "JDBC exception executing SQL [SELECT * FROM transactions WHERE secret = 1]");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccess(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("A database error occurred")
                .doesNotContain("SELECT").doesNotContain("transactions");
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("redis password=hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
    }

    @Test
    void apiExceptionsKeepTheirStatusAndMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(ApiException.badRequest("Insufficient balance. Available: 0"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Insufficient balance. Available: 0");
    }
}
