# Migrazioni del database

Flyway esegue gli script `V1`, `V2`, ecc. nell'ordine di versione e registra quelli
applicati in `flyway_schema_history`. Questi file modificano lo schema PostgreSQL;
non vengono eseguiti a ogni richiesta HTTP.

Le migrazioni già applicate restano invariate, anche nei commenti, perché Flyway
ne verifica il checksum. Questa guida ne spiega il contenuto senza alterarlo.
Per cambiare lo schema si aggiunge una nuova migrazione.

| File | Cosa fa e perché |
| --- | --- |
| `V1__create_initial_schema.sql` | Crea `app_user`, `availability_slot`, `appointment` e `review`, con chiavi esterne, controlli e indici. Le colonne `version` di slot e appuntamento sono usate da JPA per il locking ottimistico. La tabella recensioni è predisposta, ma non implica che il relativo modulo applicativo sia già implementato. |
| `V2__unique_user_email_case_insensitive.sql` | Aggiunge l'indice univoco su `LOWER(email)`: impedisce account duplicati anche con maiuscole diverse e registrazioni simultanee. |
| `V3__prevent_overlapping_slots.sql` | Introduce il vincolo PostgreSQL di esclusione sugli intervalli: nessuna sovrapposizione, anche tra inserimenti concorrenti. L'intervallo `[inizio, fine)` permette slot adiacenti. Il controllo include gli slot disabilitati. |
| `V4__unique_active_appointment_per_slot.sql` | Sostituisce l'unicità assoluta dello slot nelle visite con un indice univoco per `status <> 'CANCELLED'`. Conserva le visite annullate e permette una nuova prenotazione dopo riapertura esplicita dello slot. |

Le entità JPA rappresentano i record letti dal database. I repository le caricano
e le salvano; i service applicano le regole e costruiscono i DTO di risposta.
PostgreSQL non produce direttamente `SlotResponse` o `AppointmentResponse`.
