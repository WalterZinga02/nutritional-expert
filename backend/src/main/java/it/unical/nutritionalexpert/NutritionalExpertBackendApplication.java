/* Avvia Spring Boot: configura il contesto e rileva controller, service e repository del backend. */
package it.unical.nutritionalexpert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NutritionalExpertBackendApplication {

	/** Avvia l’applicazione Spring Boot. */
	public static void main(String[] args) {
		SpringApplication.run(NutritionalExpertBackendApplication.class, args);
	}

}
