package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = {"roleReference", "country", "phoneCode"})
    Optional<UserAccount> findByUserNameIgnoreCaseOrEmailIdIgnoreCase(String username, String email);

    Optional<UserAccount> findByUserNameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIdIgnoreCase(String email);

    boolean existsByUserNameIgnoreCase(String username);

    boolean existsByUserNameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIdIgnoreCase(String email);

    boolean existsByEmailIdIgnoreCaseAndIdNot(String email, Long id);

    List<UserAccount> findAllByOrderByUserNameAsc();

    @EntityGraph(attributePaths = {"roleReference", "country", "phoneCode"})
    List<UserAccount> findAllDetailedByOrderByUserNameAsc();

    @EntityGraph(attributePaths = {"roleReference", "country", "phoneCode"})
    @Query("""
            select u
            from UserAccount u
            where lower(trim(coalesce(u.createdBy, ''))) = lower(trim(:createdBy))
              and upper(trim(coalesce(u.role, ''))) = upper(trim(:role))
            order by lower(u.userName) asc
            """)
    List<UserAccount> findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc(
            @Param("createdBy") String createdBy,
            @Param("role") String role
    );

    @EntityGraph(attributePaths = {"roleReference", "country", "phoneCode"})
    List<UserAccount> findAllByIdOrderByUserNameAsc(Long id);

    @EntityGraph(attributePaths = {"roleReference", "country", "phoneCode"})
    Optional<UserAccount> findDetailedById(Long id);

    @EntityGraph(attributePaths = "roleReference")
    Stream<UserAccount> streamAllByOrderByUserNameAsc();

    @Query("select u.companyId from UserAccount u where u.companyId is not null and trim(u.companyId) <> ''")
    List<String> findAllCompanyIds();

    boolean existsByCompanyIdIgnoreCase(String companyId);

    @Query(value = """
            select coalesce(max(cast(substring(trim(company_id), :prefixLengthPlusOne) as integer)), 0)
            from user_schema.user_profile
            where company_id is not null
              and upper(trim(company_id)) like concat(upper(:prefix), '%')
              and substring(trim(company_id), :prefixLengthPlusOne) ~ '^[0-9]+$'
            """, nativeQuery = true)
    int findHighestCompanyIdNumber(@Param("prefix") String prefix, @Param("prefixLengthPlusOne") int prefixLengthPlusOne);

    @Query("""
            select u.userName as username, u.role as role
            from UserAccount u
            where u.userName is not null and trim(u.userName) <> ''
              and u.role is not null and trim(u.role) <> ''
            order by lower(u.userName) asc
            """)
    List<RoleAssignmentView> findRoleAssignments();

    interface RoleAssignmentView {
        String getUsername();
        String getRole();
    }
}
