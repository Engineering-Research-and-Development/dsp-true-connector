package it.eng.connector.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="PROPERTIES")
public class Property {
	
	@Id
	@Column(name = "id")
	private int idsss;
	
	private Instant CREATED_ON;
	private String APPLICATION;
	private String PROFILE;
	private String LABEL; 
	private String PROP_KEY; 
	private String VVALUE; 
}
