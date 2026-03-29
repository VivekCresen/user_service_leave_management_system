package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "roleReference")
    Optional<UserAccount> findByUserNameIgnoreCaseOrEmailIdIgnoreCase(String username, String email);

    Optional<UserAccount> findByUserNameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIdIgnoreCase(String email);

    List<UserAccount> findAllByOrderByUserNameAsc();

    @EntityGraph(attributePaths = "roleReference")
    Optional<UserAccount> findDetailedById(Long id);

    @EntityGraph(attributePaths = "roleReference")
    Stream<UserAccount> streamAllByOrderByUserNameAsc();

    @Query("select u.companyId from UserAccount u where u.companyId is not null and trim(u.companyId) <> ''")
    List<String> findAllCompanyIds();
}
