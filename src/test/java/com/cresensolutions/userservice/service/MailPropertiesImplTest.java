package com.cresensolutions.userservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailPropertiesImplTest {

    @Test
    void constructor_withAllValues_storesCorrectly() {
        MailPropertiesImpl props = new MailPropertiesImpl(
                "noreply@cresensolutions.com",
                10L,
                "http://localhost:4200/forgot-password",
                "http://localhost:4200/login",
                "/path/to/logo.png"
        );

        assertThat(props.fromAddress()).isEqualTo("noreply@cresensolutions.com");
        assertThat(props.otpExpirationMinutes()).isEqualTo(10L);
        assertThat(props.forgotPasswordUrl()).isEqualTo("http://localhost:4200/forgot-password");
        assertThat(props.loginUrl()).isEqualTo("http://localhost:4200/login");
        assertThat(props.logoPath()).isEqualTo("/path/to/logo.png");
    }

    @Test
    void constructor_withNullStrings_defaultsToEmpty() {
        MailPropertiesImpl props = new MailPropertiesImpl(null, 5L, null, null, null);

        assertThat(props.fromAddress()).isEmpty();
        assertThat(props.forgotPasswordUrl()).isEmpty();
        assertThat(props.loginUrl()).isEmpty();
        assertThat(props.logoPath()).isEmpty();
        assertThat(props.otpExpirationMinutes()).isEqualTo(5L);
    }

    @Test
    void constructor_withWhitespaceStrings_trimsValues() {
        MailPropertiesImpl props = new MailPropertiesImpl(
                "  sender@cresensolutions.com  ",
                15L,
                "  http://forgot  ",
                "  http://login  ",
                "  /logo.png  "
        );

        assertThat(props.fromAddress()).isEqualTo("sender@cresensolutions.com");
        assertThat(props.forgotPasswordUrl()).isEqualTo("http://forgot");
        assertThat(props.loginUrl()).isEqualTo("http://login");
        assertThat(props.logoPath()).isEqualTo("/logo.png");
    }
}
