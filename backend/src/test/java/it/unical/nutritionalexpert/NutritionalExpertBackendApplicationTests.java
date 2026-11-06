/* Test di avvio: verifica che il contesto Spring Boot e le sue dipendenze si inizializzino correttamente. */
package it.unical.nutritionalexpert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NutritionalExpertBackendApplicationTests {

	// Verifica che il contesto Spring si avvii con la configurazione di test.
	@Test
	void contextLoads() {
	}

}
