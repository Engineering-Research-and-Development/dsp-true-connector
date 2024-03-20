package it.eng.negotiation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity(name = "NEGOT_OFFER")
@Table(name = "NEGOT_OFFER")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class OfferEntity extends BaseEntity {

    @Column(name = "target")
    private String target;

    @Column(name = "assigner")
    private String assigner;

    @Column(name = "assignee")
    private String assignee;

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "offer_id")
    private Set<PermissionEntity> permissions;
}
