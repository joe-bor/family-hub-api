package com.familyhub.demo.repository;

import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.SharedList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedListRepository extends JpaRepository<SharedList, UUID> {

    @Query("""
            select distinct l from SharedList l
            left join fetch l.items item
            left join fetch item.category
            where l.family = :family
            order by l.createdAt desc
            """)
    List<SharedList> findByFamilyWithItems(@Param("family") Family family);

    @Query("""
            select distinct l from SharedList l
            left join fetch l.items item
            left join fetch item.category
            where l.family = :family
              and l.id = :id
            """)
    Optional<SharedList> findDetailByFamilyAndId(@Param("family") Family family, @Param("id") UUID id);
}
