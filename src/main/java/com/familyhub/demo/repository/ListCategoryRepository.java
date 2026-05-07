package com.familyhub.demo.repository;

import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.ListCategory;
import com.familyhub.demo.model.ListKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListCategoryRepository extends JpaRepository<ListCategory, UUID> {

    Optional<ListCategory> findByFamilyAndId(Family family, UUID id);

    List<ListCategory> findByFamilyAndKindOrderBySortOrderAsc(Family family, ListKind kind);
}
