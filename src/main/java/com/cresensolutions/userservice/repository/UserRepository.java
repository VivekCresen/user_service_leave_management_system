package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUserNameIgnoreCaseOrEmailIdIgnoreCase(String username, String email);

    Optional<UserAccount> findByUserNameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIdIgnoreCase(String email);

    List<UserAccount> findAllByOrderByUserNameAsc();
}
