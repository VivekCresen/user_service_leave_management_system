package com.cresensolutions.userservice.dto;

public record PhoneCodeResponse(Long id, Long countryId, String countryName, String dialCode, String flagEmoji) {}
