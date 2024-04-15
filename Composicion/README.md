# Ejemplo 3 - Asociación de Composición

## Asociaciones de composición

Una asociación de composición puede venir representada por el siguiente diagrama

![Diagrama](./uml.png)

Esta asociación, como en ejemplos anteriores, es una asociación _uno-a-muchos_ que podemos tratar bidireccionalmente, para que su gestión sea más eficiente; sin embargo, el hecho de que sea de **composición le da un tinte algo especial**.

En esta asociación, el _componente_ es una entidad débil; no tiene sentido su existencia fuera del ámbito de una instancia de un _compuesto_. Por tanto, la gestión de cada componente debe ir asociada a la gestión del compuesto.

## Operaciones en cascada

JPA nos permite realizar operaciones en cascada con entidades. ¿Recuerdas las políticas de borrado en tablas en SQL? Viene a ser lo mismo, pero más potente, ya que no solo funcionan con operaciones de borrado, sino que se pueden usar con todas las operaciones: consultar, salvar, borrar, ...

**¿Cómo conseguimos hacer estas operaciones?** Las anotaciones como `@OneToMany` pueden recibir algunos argumentos además de `mappedBy`. Entre ellos están los siguientes:

- `Cascade`: podemos indicar qué tipo de operaciones en cascada queremos que se realicen al trabajar con esta entidad. Debe ser un valor de la enumeración `javax.persistence.CascadeType`, a saber: `ALL`, `PERSIST`, `MERGE`, `REMOVE`, `DETACH`.
- `orphanRemoval`: propiedad booleana que permite indicar que si una entidad a la que hace referencia la anotación de asociación (por ejemplo, `@OneToMany`) pierde _su clave externa_ (es decir, la entidad con la que está asociada, y por tanto _queda huérfana_) se eliminará.  

Vamos a ver el código a través de un ejemplo. Sea el siguiente diagrama de clases de UML.

![UML](./png/Model!Main_0.png)

## Paso 1: Implementación de la asociación en el lado _muchos_


```java
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asiento {
	
	@Id
	@GeneratedValue
	private Long id;
	
	private int fila, columna;
	
	@Enumerated(EnumType.STRING)
	private TipoAsiento tipo;
	
	@ManyToOne
	private Avion avion;

}
```

## Paso 2: Implementación de la asociación en el lado _uno_

```java
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avion {
	
	@Id
	@GeneratedValue
	private Long id;
	
	private String modelo;
	
	private int maxPasajeros;
	
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@Builder.Default
	@OneToMany(
			mappedBy="avion", 
			fetch = FetchType.EAGER,
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	private List<Asiento> asientos = new ArrayList<>();

	
	// MÉTODOS HELPER
	
	public void addAsiento(Asiento a) {
		a.setAvion(this);
		this.asientos.add(a);
	}
	
	public void removeAsiento(Asiento a) {
		this.asientos.remove(a);
		a.setAvion(null);
		
	}
	
}

```

> En este caso, los métodos _helper_ los escribimos en el lado muchos, porque para la composición es el **lado propietario de la asociación**.

## Paso 3: Sobre repositorios

Este caso, como hemos podido comprobar, es un poco especial, ya que todo el ciclo de vida de `Asiento` se maneja a través de `Avion`. Por tanto, **no necesitamos implementar un repositorio para la clase `Asiento`**. Tan solo lo vamos a necesitar para la clase `Avion`.

```java
public interface AvionRepositorio 
	extends JpaRepository<Avion, Long> {

}
```

## Paso 4: Cómo trabajar con la asociación de composición.

Vamos a comprobar como toda la gestión del ciclo de vida sobre los `Asiento` se realiza a través del `Avion`.

```java
		Avion airbus320 = Avion.builder()
				.modelo("Airbus A320")
				.maxPasajeros(300)
				.build();
		
		for(int i = 1; i<=2;i++) {
			for(int j = 1; j<=6; j++) {
				airbus320.addAsiento(
						Asiento.builder()
						.tipo(TipoAsiento.PRIMERA)
						.fila(i)
						.columna(j)
						.build()						
						);
			}
		}
		
		for(int i = 3; i<=50;i++) {
			for(int j = 1; j<=6; j++) {
				airbus320.addAsiento(
						Asiento.builder()
						.tipo(TipoAsiento.TURISTA)
						.fila(i)
						.columna(j)
						.build()						
						);
			}
		}
		
		repositorio.save(airbus320);

```

Si revisamos el log, podemos ver que se ha generado:

- Una sentencia de inserción para el avión.
- Otras tantas sentencias de inserción para los asientos.


Si añadimos al final

```java
repositorio.delete(airbus320);
```

Podemos comprobar en el log que también se borran todos los asientos y después, el avión que queríamos borrar.

## ¿Posibles mejoras?

Una posible mejora para este ejemplo la podríamos implementar en la clase `Asiento`. En lugar de usar un identificador autogenerado, lo ideal sería que el identificador de la clase fuera compuesto por:

- El `id` del avión.
- Otros atributos que discriminen un asiento, como la `fila` y `columna`.

Esto lo trabajaremos más adelante cuando aprendamos a implementar un **identificador compuesto**.

## BONUS

En este ejemplo se ha usado un `Enum` para gestionar el tipo de asiento. Spring Data JPA gestiona los tipos enumerados almacenando un número, de forma que si tenemos la siguiente enumeración:

```java
public enum TipoAsiento {
	
	TURISTA, PRIMERA

}
```

Almacenaría para `TURISTA` un `1`, para `PRIMERA` un 2, ... Es decir, el nº de orden.

Si en lugar de almacenar un número, queremos almacenar el `name` de la opción de la enumeración, se usa la anotación `@Enumerated`, para modificar el tipo, quedando así:

```java
@Enumerated(EnumType.STRING)
private TipoAsiento tipo;
```
