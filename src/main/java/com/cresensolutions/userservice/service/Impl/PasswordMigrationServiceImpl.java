package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.PasswordMigrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

@Component
public class PasswordMigrationServiceImpl implements PasswordMigrationService {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.{56}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findAll().stream()
                .filter(this::requiresHashUpgrade)
                .forEach(user -> user.setPassword(passwordEncoder.encode(user.getPassword())));
    }

    private boolean requiresHashUpgrade(UserAccount user) {
        String password = user.getPassword();
        return password != null && !BCRYPT_PATTERN.matcher(password).matches();
    }
}
