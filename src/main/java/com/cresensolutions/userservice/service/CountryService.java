package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CountryResponse;
import com.cresensolutions.userservice.dto.PhoneCodeResponse;
import com.cresensolutions.userservice.repository.CountryRepository;
import com.cresensolutions.userservice.repository.PhoneCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CountryService {

    private final CountryRepository countryRepository;
    private final PhoneCodeRepository phoneCodeRepository;

    public CountryService(CountryRepository countryRepository, PhoneCodeRepository phoneCodeRepository) {
        this.countryRepository = countryRepository;
        this.phoneCodeRepository = phoneCodeRepository;
    }

    public List<CountryResponse> getCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CountryResponse(c.getId(), c.getName(), c.getCode(), c.getFlagEmoji()))
                .toList();
    }

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
