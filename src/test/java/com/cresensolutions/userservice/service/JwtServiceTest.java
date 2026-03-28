package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.UserAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtServiceImpl(
            "Q3Jlc2VuTGVhdmVNYW5hZ2VtZW50U3lzdGVtSldUU2VjcmV0S2V5Rm9ySFMyNTY=",
            86400000
    );

    @Test
    void shouldGenerateValidJwtToken() {
        UserAccount user = new UserAccount(
                "Admin User",
                "vivek.chavda@cresensolutions.com",
                "vivekadmin",
                "password",
                "ADMIN"
        );

        String token = jwtService.generateToken(user);

        assertEquals(3, token.split("\\.").length);
        assertEquals("vivekadmin", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, "vivekadmin"));
    }
}
