package it.eng.catalog.entity;

import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

	private static final long serialVersionUID = 2318863827225946104L;

	@Id
	@Column(name = "id")
	private String id;
	
	@CreationTimestamp
	@Column(name = "created_date", nullable = false, updatable = false)
	private Instant createdAt;
	
	@Column(name = "modified_date")
	@UpdateTimestamp
	private Instant updatedAt;
	
	@CreatedBy
	private String createdBy;
	
	@LastModifiedBy
	private String updatedBy;
	
}
