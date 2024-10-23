package it.eng.catalog.model;

import lombok.Data;

@Data
public class Artifact {

	private String id;
	private String filename;
	private String contentType;
	private long length;
    private String uploadDate;
    private String datasetId;
}
