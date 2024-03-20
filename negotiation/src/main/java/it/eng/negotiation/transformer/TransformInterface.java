package it.eng.negotiation.transformer;

public interface TransformInterface<IN, OUT> {

	Class<IN> getInputType();
	
	Class<OUT> getOutputType();

	OUT transform(IN input);
	
}
