package it.eng.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.eng.catalog.entity.ConstraintEntity;

@Repository
public interface ConstraintRepository extends JpaRepository<ConstraintEntity, String> {

}
