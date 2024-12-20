package it.eng.tools.model;

public enum ArtifactType {
	
	FILE("file"), 
	EXTERNAL("external");

	private final String type;

	ArtifactType(String type) {
		this.type = type;
	}

	public String type() {
		return type;
	}

}
