package com.examplex.demo.repository;

import com.examplex.demo.model.LoginManagementGroups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginManagementGroupsRepository extends JpaRepository<LoginManagementGroups, Integer> {

    /**
     * Busca um grupo pelo ID
     */
    Optional<LoginManagementGroups> findById(Integer id);

    /**
     * Busca um grupo pelo UUID
     */
    Optional<LoginManagementGroups> findByUuid(String uuid);
}