package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findAllByOrderByIdAsc();

    Optional<Role> findByUniqueNameIgnoreCase(String uniqueName);
}
