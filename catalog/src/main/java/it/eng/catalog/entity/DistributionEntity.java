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

@Entity(name="DISTRIBUTION")
@Table(name="CAT_DISTRIBUTION")
@Data
@EqualsAndHashCode(callSuper = false)
public class DistributionEntity extends BaseEntity {

	private static final long serialVersionUID = -2333703579900266240L;

	private String title;
	private String format;
	private String issued;
	private String modified;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DISTRIBUTION_ID")
	private Set<OfferEntity> hasPolicies;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DISTRIBUTION_ID")
	private Set<DataServiceEntity> accessServices;

	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DISTRIBUTION_ID")
	private Set<MultilanguageEntity> descriptions;
	
}
