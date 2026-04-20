package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.service.Impl.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(SECRET, EXPIRATION_MS);
    }
    
    @Test
    void generateToken_returnsNonBlankToken() {
        UserAccount user = buildUser("alice", "alice@cresensolutions.com", "EMPLOYEE");
        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateToken_nullUser_returnsToken() {
        String token = jwtService.generateToken(null);
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsCorrectSubject() {
        UserAccount user = buildUser("bob", "bob@cresensolutions.com", "MANAGER");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractUsername(token)).isEqualTo("bob");
    }

    @Test
    void extractUsername_invalidToken_throwsRuntimeException() {
        assertThatThrownBy(() -> jwtService.extractUsername("not.a.token"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void isTokenValid_matchingUsername_returnsTrue() {
        UserAccount user = buildUser("carol", "carol@cresensolutions.com", "ADMIN");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token, "carol")).isTrue();
    }

    @Test
    void isTokenValid_wrongUsername_returnsFalse() {
        UserAccount user = buildUser("dave", "dave@cresensolutions.com", "EMPLOYEE");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token, "other")).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_throwsRuntimeException() {
        JwtServiceImpl shortLived = new JwtServiceImpl(SECRET, -1L); // already expired
        UserAccount user = buildUser("eve", "eve@cresensolutions.com", "EMPLOYEE");
        String token = shortLived.generateToken(user);
        assertThatThrownBy(() -> shortLived.isTokenValid(token, "eve"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void generateToken_userWithNullRole_usesEmptyRole() {
        UserAccount user = new UserAccount();
        user.setUsername("norole");
        user.setEmail("norole@cresensolutions.com");
        user.setFullName("No Role");
        user.setActive(true);

        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("norole");
    }

    @Test
    void isTokenValid_tamperedToken_throwsRuntimeException() {
        UserAccount user = buildUser("frank", "frank@cresensolutions.com", "EMPLOYEE");
        String token = jwtService.generateToken(user) + "tampered";
        assertThatThrownBy(() -> jwtService.isTokenValid(token, "frank"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void generateToken_userWithNullEmail_throwsOrHandles() {
        UserAccount user = new UserAccount();
        user.setUsername("noemail");
        user.setFullName("No Email");
        user.setActive(true);
        user.assignRole(new Role(1L, "EMPLOYEE", "EMPLOYEE"));
        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(RuntimeException.class);
    }


    private UserAccount buildUser(String username, String email, String roleName) {
        Role role = new Role(1L, roleName, roleName);
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName("Test User");
        user.assignRole(role);
        user.setActive(true);
        return user;
    }
}
