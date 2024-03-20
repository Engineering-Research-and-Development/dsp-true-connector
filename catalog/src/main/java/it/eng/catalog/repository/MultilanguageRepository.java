package it.eng.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.eng.catalog.entity.MultilanguageEntity;

@Repository
public interface MultilanguageRepository extends JpaRepository<MultilanguageEntity, String> {

}
