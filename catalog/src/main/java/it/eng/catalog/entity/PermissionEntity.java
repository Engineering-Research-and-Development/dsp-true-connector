package it.eng.catalog.entity;

import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(name="PERMISSION")
@Table(name="OFFER_PERMISSION")
@Data
@EqualsAndHashCode(callSuper = false)
public class PermissionEntity extends BaseEntity {

	private static final long serialVersionUID = -1263528994626744233L;
	private String assigner;
	private String assignee;
	private String target;
	private String action;
	@OneToMany(cascade = CascadeType.ALL)
	@JoinColumn(name = "PERMISSION_ID")
	private Set<ConstraintEntity> constraint;
}
