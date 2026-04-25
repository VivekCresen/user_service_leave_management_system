package com.cresensolutions.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "user_schema", name = "country")
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "flag_emoji", length = 10)
    private String flagEmoji;

    public Country() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getFlagEmoji() { return flagEmoji; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCode(String code) { this.code = code; }
    public void setFlagEmoji(String flagEmoji) { this.flagEmoji = flagEmoji; }
}
