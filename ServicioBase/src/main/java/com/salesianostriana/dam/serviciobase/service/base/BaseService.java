package com.salesianostriana.dam.serviciobase.service.base;

import java.util.List;
import java.util.Optional;

public interface BaseService<T, ID> {
	//T es el tipo de dato de la entidad
	//id es el tipo de dato del identificador de T
	
	List<T> findAll();
	
	Optional<T> findById(ID id);
	
	T save(T t);
	
	T edit(T t);
	
	void delete(T t);
	
	void deleteById(ID id);
	

}
