/* Converte le eccezioni dei controller/service in risposte JSON leggibili, con codice HTTP e messaggio per Angular. */
package it.unical.nutritionalexpert.common;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    /** Restituisce 400 con gli errori dei singoli campi del DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message", "Controlla i dati inseriti.", "errors", fields));
    }
    /** Mantiene codice HTTP e messaggio delle eccezioni applicative del service. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
    /** Traduce una violazione dei vincoli del database in conflitto 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(Map.of("message", "I dati sono già presenti o in conflitto. Aggiorna l'elenco e controlla i valori inseriti."));
    }
    /** Traduce il fallimento del locking ottimistico in 409: il client deve ricaricare i dati. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<?> concurrentUpdate(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(Map.of("message", "I dati sono stati modificati da un'altra richiesta. Aggiorna e riprova."));
    }
    /** Segnala con 400 parametri mancanti o non convertibili, come date e ID. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<?> invalidParameter(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Parametri mancanti o non validi. Controlla date e identificativi."));
    }
    /** Segnala con 400 JSON non leggibile o valori incompatibili con il DTO. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "La richiesta contiene dati non validi."));
    }
}
