package it.eng.catalog.entity;

import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity(name="DATASERVICE")
@Table(name="CAT_DATASERVICE")
@Data
@EqualsAndHashCode(callSuper = false)
public class DataServiceEntity extends Resource {

	private static final long serialVersionUID = -7018976737419530829L;
	
	private String endpointDescription;
    private String endpointURL;
    
    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
	@JoinColumn(name = "DATASERVICE_ID")
    private Set<DatasetEntity> servesDatasets;
    
}
