package it.eng.negotiation.entity;

import it.eng.negotiation.model.Action;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Entity(name = "NEGOT_PERMISSION")
@Table(name = "NEGOT_PERMISSION")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class PermissionEntity extends BaseEntity {

    @Column(name = "assigner")
    private String assigner;

    @Column(name = "assignee")
    private String assignee;

    @Column(name = "target")
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private Action action;

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "permission_id")
    private Set<ConstraintEntity> constraints;
}
