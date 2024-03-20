package it.eng.catalog.mapper;

public interface MapperInterface<Entity, Model> {

	Model entityToModel (Entity entity);
	Entity modelToEntity (Model model);
}
