package com.shansuda.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.http.HttpStatus;

class UpstreamErrorHandlerTest {

    @Test
    void mapsConnectAndTimeoutToUpstream() {
        assertTrue(UpstreamErrorHandler.isUpstream(new ConnectException("Connection refused")));
        assertTrue(UpstreamErrorHandler.isUpstream(new TimeoutException("timed out")));
        assertTrue(UpstreamErrorHandler.isUpstream(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)));
        assertFalse(UpstreamErrorHandler.isUpstream(new IllegalArgumentException("bad")));
    }
}
