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

@Entity(name="DATASET")
@Table(name="CAT_DATASET")
@Data
@EqualsAndHashCode(callSuper = false)
public class DatasetEntity extends Resource {

	private static final long serialVersionUID = 7947086815650129015L;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DATASET_ID")
	private Set<OfferEntity> hasPolicy;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DATASET_ID")
	private Set<DistributionEntity> distribution;

}
