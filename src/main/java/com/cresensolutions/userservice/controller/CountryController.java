package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.CountryResponse;
import com.cresensolutions.userservice.dto.PhoneCodeResponse;
import com.cresensolutions.userservice.repository.CountryRepository;
import com.cresensolutions.userservice.repository.PhoneCodeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CountryRepository countryRepository;
    private final PhoneCodeRepository phoneCodeRepository;

    public CountryController(CountryRepository countryRepository, PhoneCodeRepository phoneCodeRepository) {
        this.countryRepository = countryRepository;
        this.phoneCodeRepository = phoneCodeRepository;
    }

    @GetMapping
    public List<CountryResponse> getCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CountryResponse(c.getId(), c.getName(), c.getCode(), c.getFlagEmoji()))
                .toList();
    }

    @GetMapping("/phone-codes")
    public List<PhoneCodeResponse> getPhoneCodes() {
        return phoneCodeRepository.findAllWithCountryOrderByCountryName().stream()
                .map(p -> new PhoneCodeResponse(
                        p.getId(),
                        p.getCountry().getId(),
                        p.getCountry().getName(),
                        p.getDialCode(),
                        p.getCountry().getFlagEmoji()))
                .toList();
    }
}
