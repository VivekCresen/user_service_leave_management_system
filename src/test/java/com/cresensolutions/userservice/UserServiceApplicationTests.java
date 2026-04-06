package com.cresensolutions.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false"
})
class UserServiceApplicationTests {

    @Test
    void  contextLoads() {
    }

}
