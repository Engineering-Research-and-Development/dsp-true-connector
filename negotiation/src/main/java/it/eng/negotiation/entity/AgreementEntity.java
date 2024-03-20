package it.eng.negotiation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Entity(name = "NEGOT_AGREEMENT")
@Table(name = "NEGOT_AGREEMENT")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class AgreementEntity extends BaseEntity {

    @Column(name = "assigner")
    private String assigner;

    @Column(name = "assignee")
    private String assignee;

    @Column(name = "target")
    private String target;

    @Column(name = "timestamp")
    private String timestamp;

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "agreement_id")
    private Set<PermissionEntity> permissions;
}
