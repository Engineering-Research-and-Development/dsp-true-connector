package it.eng.negotiation.entity;

import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity(name = "NEGOT_CONSTRAINT")
@Table(name = "NEGOT_CONSTRAINT")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ConstraintEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "left_operand")
    private LeftOperand leftOperand;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator")
    private Operator operator;

    @Column(name = "right_operand")
    private String rightOperand;
}