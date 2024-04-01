package it.eng.connector.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "properties")
public class Property {
	
	@Id
	@Field(name = "id")
	private int idsss;
	
	private Instant CREATED_ON;
	private String APPLICATION;
	private String PROFILE;
	private String LABEL; 
	private String PROP_KEY; 
	private String VVALUE; 
}
