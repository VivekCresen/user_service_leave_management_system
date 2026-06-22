package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.PhoneCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PhoneCodeRepository extends JpaRepository<PhoneCode, Long> {

    @Query("select p from PhoneCode p join fetch p.country order by p.country.name asc")
    List<PhoneCode> findAllWithCountryOrderByCountryName();
}
