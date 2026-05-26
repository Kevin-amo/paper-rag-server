package com.lqr.paperragserver.auth.web;

import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.UserAvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UserAvatarService userAvatarService = mock(UserAvatarService.class);
    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, userAvatarService);
    }

    @Test
    void logoutShouldRevokeCurrentBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer current-token");

        controller.logout(request);

        verify(authService).logout("current-token");
    }

    @Test
    void logoutShouldIgnoreMissingBearerToken() {
        controller.logout(new MockHttpServletRequest());

        verify(authService).logout(null);
    }

    @Test
    void logoutShouldIgnoreNonBearerAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc");

        controller.logout(request);

        verify(authService).logout(null);
        verify(authService, never()).logout("abc");
    }
}