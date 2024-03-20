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

@Entity(name="OFFER")
@Table(name="OFFER_OFFER")
@Data
@EqualsAndHashCode(callSuper = false)
public class OfferEntity extends BaseEntity {

	private static final long serialVersionUID = -3375013272854903836L;
	
	private String target;
	private String assigner;
	private String assignee;
	
	@OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "OFFER_ID")
	private Set<PermissionEntity> permissions;
	
}
