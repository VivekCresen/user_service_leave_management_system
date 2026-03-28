package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.UserAccount;

public interface JwtService {

    String generateToken(UserAccount user);

    String extractUsername(String token);

    boolean isTokenValid(String token, String expectedUsername);
}
