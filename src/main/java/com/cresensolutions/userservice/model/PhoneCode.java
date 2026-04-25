package com.cresensolutions.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(schema = "user_schema", name = "phone_code")
public class PhoneCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", foreignKey = @ForeignKey(name = "fk_phone_code_country"))
    private Country country;

    @Column(name = "dial_code", nullable = false, length = 10)
    private String dialCode;

    public PhoneCode() {}

    public Long getId() { return id; }
    public Country getCountry() { return country; }
    public String getDialCode() { return dialCode; }

    public void setId(Long id) { this.id = id; }
    public void setCountry(Country country) { this.country = country; }
    public void setDialCode(String dialCode) { this.dialCode = dialCode; }
}
