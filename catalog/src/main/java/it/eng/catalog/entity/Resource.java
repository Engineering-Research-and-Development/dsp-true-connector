package it.eng.catalog.entity;

import java.util.Set;

import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Resource extends BaseEntity {

	private static final long serialVersionUID = -2193817179533390362L;

	/**
	 * This is in format of a CSV (comma-separated values).
	 * When converting to Model you have to split it and vice versa, before persisting to DB
	 */
	private String keyword;
	
	/**
	 * This is in format of a CSV (comma-separated values).
	 * When converting to Model you have to split it and vice versa, before persisting to DB
	 */
	private String theme;
	private String conformsTo;
	private String creator;
	@OneToMany(fetch = FetchType.EAGER)
	private Set<MultilanguageEntity> description;
	private String identifier;
	private String issued;
	private String modified;
	private String title;
}
