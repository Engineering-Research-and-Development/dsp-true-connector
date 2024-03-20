package it.eng.catalog.entity;

import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(name="CATALOG")
@Table(name="CAT_CATALOG")
@Data
@EqualsAndHashCode(callSuper = false)
public class CatalogEntity extends Resource {

	private static final long serialVersionUID = 758545289738044362L;

	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "CATALOG_ID")
	private Set<DistributionEntity> distribution;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "CATALOG_ID")
	private Set<DatasetEntity> dataset;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "CATALOG_ID")
	private Set<DataServiceEntity> service;
	
	private String participantId;
	private String homepage;
	
}
