package com.familyhub.demo.repository;

import com.familyhub.demo.model.Chore;
import com.familyhub.demo.model.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChoreRepository extends JpaRepository<Chore, UUID> {

    @Query("select c from Chore c join fetch c.assignedToMember m where c.family = :family and m.family = :family")
    List<Chore> findByFamilyWithAssignee(@Param("family") Family family);

    @Query("""
            select c from Chore c
            join fetch c.assignedToMember m
            where c.family = :family
              and c.id = :id
              and m.family = :family
            """)
    Optional<Chore> findByFamilyAndId(@Param("family") Family family, @Param("id") UUID id);
}
