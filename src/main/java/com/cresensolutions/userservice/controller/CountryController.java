package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.CountryResponse;
import com.cresensolutions.userservice.dto.PhoneCodeResponse;
import com.cresensolutions.userservice.service.CountryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping
    public List<CountryResponse> getCountries() {
        return countryService.getCountries();
    }

    @GetMapping("/phone-codes")
    public List<PhoneCodeResponse> getPhoneCodes() {
        return countryService.getPhoneCodes();
    }
}
