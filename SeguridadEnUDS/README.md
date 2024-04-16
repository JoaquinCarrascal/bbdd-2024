
# Ejemplo 13 - Ejemplo de uso de Spring Security con servicio de autenticación  

Como hemos visto en el [ejemplo anterior](../SeguridadEnMemoria/), el proceso de autenticación nos permite responder a la pregunta ¿y tú, quién eres?. En dicho ejemplo, hemos hecho la proceso  en memoria. Vamos a proceder ahora a crear un servicio de autenticación, que nos permitirá dar toda la versatilidad a dicho proceso.

## Modelos de autenticación

- _In Memory_: lo hemos aprendido en el ejemplo anterior.
- _JDBC_: los usuarios se almacenan en una base de datos relacional, accedida a través de Jdbc.
- _ldap_: los usuarios están en un almacén de seguridad, como por ejemplo de directorio activo de un servidor Windows.
- ***User Details Service***: se accede a la información de autenticación a través de un servicio. 

## Interfaces clave

Dentro del modelo de clases e interfaces de Spring Security, encontramos algunos de ellos que serán claves en el proceso de autenticación, como son:

- [`org.springframework.security.core.userdetails.UserDetails`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/userdetails/UserDetails.html): **Proporciona información básica de un usuario**.
Las implementaciones no son utilizadas directamente por Spring Security por motivos de seguridad. Simplemente almacenan información de usuario que luego se encapsula en objectos de tipo `Authentication`. Esto permite separar  la información del usuario no relacionada con la seguridad (como direcciones de correo electrónico, números de teléfono, etc.). **Suele interesar implementar esta interfaz en lugar a usar directamente la clase `org.springframework.security.core.userdetails.User`**. 
- [`org.springframework.security.core.userdetails.UserDetailsService`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/userdetails/UserDetailsService.html): interfaz principal que carga los datos de un usuario. Se utiliza en todo el framework como un DAO de usuarios. Solo proporciona un método, y este es de solo lectura. 
 - [`org.springframework.security.core.GrantedAuthority`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/GrantedAuthority.html). Representa una autorización (un privilegio concreto) otorgado a un objeto de tipo `Authentication`.Podemos considerar a cada `GrantedAuthority` como un privilegio individual:  `READ_AUTHORITY`, `WRITE_PRIVILEGE` o incluso `CAN_EXECUTE_AS_ROOT`. _Lo importante a entender es que el nombre es **arbitrario**_. De manera similar, en Spring Security, podemos pensar en cada rol como una `GrantedAuthority` de _grano grueso_ que se representa como una cadena y tiene el prefijo "ROLE". 

## Paso 1: Nuestro modelo de usuario

En vista de lo que hemos leído en el apartado anterior, podemos plantear dos posibles soluciones para la implementación de nuestro modelo `Usuario`:

- Que nuestra propia clase `Usuario` implemente la interfaz `UserDetails`.
- A partir de nuestra clase `Usuario`, utilizar la clase `UserBuilder` para construir un objeto de tipo `UserDetails` en las circunstancias que sean necesarias.

**Escogemos la primera de ellas**.

```java
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario implements UserDetails {

	@Id
	@GeneratedValue
	private Long id;
	
	private String username, password;
	
	private boolean admin;
	
	
	
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		String role = "ROLE_";
		role += (admin) ? "ADMIN" : "USER";
		return List.of(new SimpleGrantedAuthority(role));
	}	

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}
```

Como es lógico, creamos un repositorio y servicio para esta entidad. Veamos el código, porque incluimos una consulta:

```java
public interface UsuarioRepositorio 
	extends JpaRepository<Usuario, Long>{
	
	Optional<Usuario> findFirstByUsername(String username);

}
```

> Por no ser estrictamente necesario, en este ejemplo no se creará el servicio, pero podría hacerse igual que en los ejemplos anteriores.

Dicha consulta nos permitirá encontrar un usuario en base a su `username`.

## Paso 2: Implementación de `UserDetailsService`

Creamos una nueva clase que implemente este interfaz:

```java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService{

	private final UsuarioRepositorio repo;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return repo.findFirstByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("Error al buscar el usuario"));
	}

}
```

El único método que nos obliga a implementar la interfaz es `loadByUsername`, un método que pueda buscar un usuario donde estén almacenados en base al campo `username`.

Aprovechando que hemos implementado la consulta, podemos devolver el usuario, si es que se encuentra, o lanzar una excepción de tipo `UsernameNotFoundException` en caso de que no.

## Paso 3: Actualización de la configuración de la seguridad

En primer lugar, vamos a crear un bean de tipo `PasswordEncoder` que nos permita cifrar las contraseñas. 

```java
@Configuration
public class PasswordEncoderConfig {
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
```

> En el ejemplo anterior ya hemos visto como funciona `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.

Ahora, necesitamos modificar código del ejemplo anterior:

- Como ya no vamos a realizar la autenticación con usuarios en memoria, podemos comentar o eliminar este bean:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
		
	
	/*@Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.builder()
        		.username("admin")
        		.password("{noop}admin")
        		.roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(user);
    }*/

	// Resto del código
}
```

- Inyectamos como dependencias los beans de tipo `UserDetailsService` y `PasswordEncoder` que hemos creado:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	
	// Resto del código
}
```

- Modificamos el bean de tipo `DaoAuthenticationProvider` para asignarle el nuevo `userDetailsService` así como el nuevo `passwordEncoder`.


```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	

	@Bean 
	public DaoAuthenticationProvider daoAuthenticationProvider() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	// Resto del código
}
```

- ya que tenemos dos roles de usuario, vamos a diferenciar qué puede hacer cada uno de ellos. Además, indicamos expresamente la ruta del logout, así como otra configuración adicional que nos va a permitir acceder a la consola de h2 sin problemas.

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	
	@Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    	http.authorizeHttpRequests(
				(authz) -> authz.requestMatchers("/css/**", "/js/**", "/h2-console/**")
				.permitAll().anyRequest().authenticated())
			.formLogin((loginz) -> loginz
					.loginPage("/login").permitAll());
		
		// Añadimos esto para poder seguir accediendo a la consola de H2
		// con Spring Security habilitado.
    	http.csrf(csrfz -> csrfz.disable());
    	http.headers(headersz -> headersz
    			.frameOptions(frameOptionsz -> frameOptionsz.disable()));
		
		return http.build();
	}

	// Resto del código
}
```

## Paso 4: Zona de administración

Creamos un controlador y una plantilla superbásicos en la ruta `/admin/`, para tener dos zonas separadas: para usuarios y para administradores.

```java
@Controller
@RequestMapping("/admin")
public class AdminControlador {
	
	@GetMapping("/")
	public String index() {
		return "admin";
	}

}
```

Creamos la plantilla `admin.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
	<form th:action="@{/logout}" method="POST" id="logoutForm"></form>
	<h1>
		Bienvenido, administrador:  <span sec:authentication="name">Usuario</span>
	</h1>
	<a href="javascript:document.getElementById('logoutForm').submit()">Salir</a>

</body>
</html>
```

## Paso adicional: Datos de prueba

Creamos a través de un componente algunos datos de prueba para poder loguearnos. ¡OJO! Hay que tener en cuenta antes de almacenar un usuario **tenemos que codificar su contraseña**.

```java
@Component
@RequiredArgsConstructor
public class InitData {
	
	private final UsuarioRepositorio repo;
	private final PasswordEncoder passwordEncoder;
	
	@PostConstruct
	public void init() {
		
		Usuario usuario = Usuario.builder()
				.admin(false)
				.username("user")
				//.password("1234")
				.password(passwordEncoder.encode("1234"))
				.build();
		
		Usuario admin = Usuario.builder()
				.admin(true)
				.username("admin")
				.password(passwordEncoder.encode("admin"))
				.build();
		
		repo.saveAll(List.of(usuario, admin));
		
	}

}
```

> Estos datos de prueba también los podríamos haber cargado a través del fichero `import.sql` como en otras ocasiones.




