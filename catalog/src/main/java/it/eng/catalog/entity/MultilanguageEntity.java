package it.eng.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(name="MULTILANGUAGE")
@Table(name="MULTILANGUAGE")
@Data
@EqualsAndHashCode(callSuper = false)
public class MultilanguageEntity extends BaseEntity {

	private static final long serialVersionUID = -790451288260597231L;
	@Column(name="val")
	private String value;
	@Column(name="lang")
	private String language;
}
