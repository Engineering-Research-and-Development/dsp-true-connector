package it.eng.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(name="CONSTRAINT")
@Table(name="OFFER_CONSTRAINT")
@Data
@EqualsAndHashCode(callSuper = false)
public class ConstraintEntity extends BaseEntity {

	private static final long serialVersionUID = -4210926678379318468L;
	
	@Column(name = "LEFT_OPERAND")
	private String leftOperand;
    private String operator;
    @Column(name = "RIGHT_OPERAND")
	private String rightOperand;

}
