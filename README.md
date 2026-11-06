# Nutritional Expert

Piattaforma web per i clienti di uno studio di nutrizione.

Per orientarsi tra file, funzioni e flussi frontend/backend, consultare la
[guida al codice](docs/GUIDA_CODICE.md). I sorgenti contengono commenti sintetici in italiano.

## Stato attuale

Registrazione e login collegati tra Angular e Spring Boot, visualizzazione e modifica del profilo personale e logout.
Sono disponibili anche la gestione degli slot ADMIN e la prenotazione delle visite con elenco personale.
L'ADMIN può consultare le prenotazioni e confermarle, completarle o annullarle.
L'utente può annullare i propri appuntamenti futuri richiesti o confermati.
Homepage dello studio e recensioni sono i prossimi moduli.

- Backend: Java 17, Spring Boot, Spring Security, JPA, PostgreSQL e Flyway.
- Frontend: Angular SPA con form reattivi, rotte protette e messaggi in italiano.

## Avvio locale

Prerequisiti: Java 17, Node.js 24, PostgreSQL 17. Creare un database vuoto chiamato
`nutritional_expert`; Flyway applica le migrazioni all'avvio.

Dal terminale nella radice del progetto:

```powershell
cd backend
.\run-local.ps1
```

Lo script chiede la password dell'utente PostgreSQL `postgres` senza mostrarla e genera
una chiave JWT casuale temporanea se `JWT_SECRET` non è già impostata.
In questo caso, dopo il riavvio del backend bisogna accedere nuovamente.

In un secondo terminale:

```powershell
cd frontend
npm ci
npm start
```

Aprire http://localhost:4200. Le richieste `/api/**` vengono inoltrate al backend
su http://localhost:8080 tramite `proxy.conf.json`.

Per una configurazione diversa, impostare `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
e `JWT_SECRET` nell'ambiente e avviare `.\mvnw.cmd spring-boot:run` da `backend`.
`.env.example` elenca le variabili: Spring Boot e lo script **non caricano automaticamente**
un file `.env`. Non aggiungere credenziali a Git.

`JWT_SECRET` deve essere una chiave casuale di almeno 32 byte codificata Base64.
Per mantenerla nella sola sessione PowerShell senza stamparla:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

## Autenticazione

| Endpoint | Accesso | Risultato |
| --- | --- | --- |
| POST /api/auth/register | Pubblico | 201, JWT e profilo |
| POST /api/auth/login | Pubblico | 200, JWT e profilo |
| GET /api/auth/me | Bearer JWT | Profilo dell'utente autenticato |
| PATCH /api/auth/me | Bearer JWT | Aggiorna nome, cognome e telefono dell'utente autenticato |
| GET /api/public/health | Pubblico | Stato del backend |

Registrazione: `email`, `password`, `firstName`, `lastName`, `phone` facoltativo.
Login: `email` e `password`. La risposta contiene `accessToken`, `tokenType`,
`expiresAt` e `user`; non contiene password o hash.

- Password da 8 a 72 caratteri, protette con PBKDF2 e salt casuale.
- Email normalizzate; unicità senza distinzione tra maiuscole e minuscole,
  verificata anche dal database e in caso di registrazioni simultanee.
- Le registrazioni pubbliche creano sempre utenti USER.
- JWT firmati HS256, validi 30 minuti, con verifica di firma, scadenza e issuer.
- Le API `/api/admin/**` richiedono il ruolo ADMIN.
- Risposte 400 per dati non validi, 409 per duplicati, 401 per credenziali/token
  non validi e 403 per permessi insufficienti.
- Il frontend conserva soltanto token e scadenza in `sessionStorage` e ricarica il
  profilo da `/me` al refresh. La sessione termina alla scadenza o al logout.
- Il logout elimina la sessione dal browser. Non revoca un JWT già emesso:
  quel token resta valido fino alla scadenza. Non sono previsti refresh token.
- La conferma password è verificata nel frontend e non viene inviata al backend.

In distribuzione, servire frontend e API sulla stessa origine con HTTPS e un reverse
proxy per `/api`. Il proxy Angular è destinato allo sviluppo.

## Modifica profilo

Dall'area personale, premere **Modifica profilo**, aggiornare i dati e scegliere
**Salva modifiche**. **Annulla** scarta le modifiche del modulo. Email e password
non sono modificabili con questa operazione.

`PATCH /api/auth/me` accetta `firstName`, `lastName` e `phone`. I campi omessi o
`null` rimangono invariati; `phone: ""` rimuove il numero. Nome e cognome, se
presenti, devono contenere da 1 a 100 caratteri dopo la rimozione degli spazi esterni.
Il telefono facoltativo segue lo stesso formato della registrazione.
L'identificativo viene ricavato esclusivamente dal JWT: ID, email, ruolo e password
eventualmente inviati nel JSON non vengono applicati. Il salvataggio avviene in una
transazione e restituisce il profilo aggiornato senza rinnovare il token.
Non sono necessarie nuove migrazioni: i campi erano già presenti in `app_user`.

## Disponibilità dello studio

Un account ADMIN accede a **Disponibilità studio** (`/admin/disponibilita`) per
creare, consultare, disabilitare e riattivare gli slot. Gli orari sono mostrati nel
fuso del browser, indicato nella pagina, e inviati alle API come istanti UTC.

| API ADMIN | Operazione |
| --- | --- |
| GET /api/admin/availability?from=...&to=... | Elenco nell'intervallo, massimo 31 giorni |
| POST /api/admin/availability | Creazione con `startTime` ed `endTime` |
| PATCH /api/admin/availability/{id} | Cambio disponibilità con `available` e `version` |

Gli slot devono iniziare nel futuro, durare al massimo 24 ore e non sovrapporsi.
Gli intervalli adiacenti sono consentiti. Anche gli slot disabilitati occupano il
proprio intervallo nell'agenda: si possono riattivare, senza crearne una copia.
Non è possibile cambiare disponibilità a slot già iniziati o associati a un
appuntamento non annullato. La versione impedisce aggiornamenti concorrenti persi.

La migrazione V3 impone l'assenza di sovrapposizioni anche in PostgreSQL, per
proteggere dalle creazioni simultanee. Se esistono già slot sovrapposti, occorre
risolverli prima di applicare la migrazione, che non elimina dati automaticamente.

Per il primo ADMIN, registrare normalmente un account e promuovere **solo quello
del professionista** nel database, sostituendo l'indirizzo nell'esempio:

```sql
UPDATE app_user SET role = 'ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE email = 'professionista@example.it'
RETURNING id, email, role;
```

Effettuare logout e login per ottenere un JWT con il nuovo ruolo.
Non vengono creati amministratori o password predefiniti dall'applicazione.

## Prenotazione appuntamenti

Dopo il login, aprire **Appuntamenti** (`/appuntamenti`), scegliere una giornata,
selezionare uno slot libero e il tipo di visita, quindi premere **Richiedi appuntamento**.
Sono disponibili pesata/BMI, elaborazione dieta, revisione dieta e consulenza alimentare.
La ricevuta mostra numero, data e stato **Richiesto**: non equivale alla conferma dello
studio. La stessa pagina mostra esclusivamente gli appuntamenti dell'utente corrente.

| API autenticate | Operazione |
| --- | --- |
| GET /api/appointments/available-slots?from=...&to=... | Slot futuri, abilitati e non prenotati; massimo 31 giorni |
| POST /api/appointments | Prenotazione con `slotId`, `version` dello slot e `type` |
| GET /api/appointments/me | Appuntamenti personali ordinati per data decrescente |

Il backend ricava l'utente esclusivamente dal JWT. ID utente o stato eventualmente
inviati nel JSON non vengono applicati: una nuova prenotazione nasce come `REQUESTED`.
Una transazione aggiorna lo slot a non disponibile e salva l'appuntamento. L'update
dello slot con `@Version` viene eseguito prima dell'inserimento: due utenti sullo stesso
slot, oppure utente e ADMIN in concorrenza, non possono entrambi modificare la medesima
versione. Il conflitto restituisce 409 e Angular aggiorna le disponibilità e l'elenco.

La migrazione V4 sostituisce l'unicità assoluta dell'appuntamento per slot con un
indice univoco sui soli appuntamenti non annullati. Questo conserva lo storico degli
annullati e permette di offrire nuovamente uno slot dopo un annullamento e una
riattivazione esplicita da parte dell'ADMIN.

### Annullamento da parte dell'utente

In **Appuntamenti → I miei appuntamenti**, il pulsante **Annulla appuntamento**
apre una conferma con i dettagli della visita. Sono annullabili solo visite proprie
`REQUESTED` o `CONFIRMED` prima dell'inizio; le visite iniziate, completate o già
annullate non sono modificabili dall'utente.

`PATCH /api/appointments/{id}/cancel` richiede il JWT e un JSON `{"version":0}`.
Il backend ricava il proprietario dal token e cerca l'appuntamento per ID e utente:
un ID inesistente o appartenente ad altri restituisce 404. La versione non aggiornata
o uno stato/orario non annullabile restituisce 409; payload non validi restituiscono 400.
L'aggiornamento transazionale a `CANCELLED` usa `@Version`, anche in concorrenza
con la conferma ADMIN. Angular ricarica l'elenco dopo il successo o un conflitto.

L'appuntamento resta nello storico e lo slot resta disabilitato: la riapertura
è una scelta esplicita dell'ADMIN. Non servono nuove migrazioni del database.
I test coprono proprietà, autenticazione, validazione, stato/orario, storico,
concorrenza utente/ADMIN e conferma/errori nell'interfaccia Angular.

Per provare il flusso: creare uno slot futuro come ADMIN, uscire, accedere con un
account USER e prenotarlo. L'utente vede la richiesta nell'elenco personale; tornando
come ADMIN, lo slot risulta **Prenotato** e non è modificabile dalla pagina disponibilità.
Al riavvio del backend Flyway applica le nuove migrazioni al database configurato.

## Gestione appuntamenti ADMIN

Accedere come ADMIN e aprire **Prenotazioni studio** (`/admin/appuntamenti`). La pagina
mostra gli appuntamenti del giorno scelto, filtrabili per stato, con nome, cognome,
email e telefono del cliente. Ogni azione presenta un riepilogo prima del salvataggio.

| Transizione | Regola |
| --- | --- |
| REQUESTED → CONFIRMED | Solo prima dell'inizio della visita |
| CONFIRMED → COMPLETED | Solo dopo il termine dello slot |
| REQUESTED / CONFIRMED → CANCELLED | Consentita all'ADMIN, anche per sistemare richieste passate |
| Da COMPLETED / CANCELLED | Nessuna transizione: stati definitivi |

Le API, riservate al ruolo ADMIN, sono:

- `GET /api/admin/appointments?from=...&to=...&status=...`: intervallo massimo di 31 giorni;
  `status` facoltativo. Restituisce dati della visita e contatti del cliente, senza credenziali.
- `PATCH /api/admin/appointments/{id}/status`: corpo `{"status":"CONFIRMED","version":0}`.
  La versione è quella dell'appuntamento ricevuto nell'elenco, non quella dello slot.

Il cambio di stato avviene in una transazione. Il confronto della versione richiesta
e `@Version` sull'entità `Appointment` impediscono che due modifiche concorrenti vadano
entrambe a buon fine. Transizioni non ammesse e versioni obsolete restituiscono 409;
Angular mantiene il messaggio di errore e aggiorna l'elenco. L'utente vede il nuovo
stato aggiornando la propria pagina Appuntamenti.

L'annullamento conserva l'appuntamento nello storico e **non riapre automaticamente
lo slot**. L'ADMIN può riattivare uno slot futuro dalla pagina Disponibilità studio,
se desidera offrirlo nuovamente. Conferma e completamento mantengono lo slot occupato.
Non sono necessarie nuove migrazioni per queste operazioni e non vengono inviate email.

## Verifiche

```powershell
cd backend
.\mvnw.cmd test
```

I test predefiniti usano H2 in memoria, senza credenziali locali, e non eseguono
le migrazioni PostgreSQL. Coprono registrazione, hashing, login, duplicati,
registrazioni concorrenti, validazione, autorizzazione e token non validi/scaduti.

Per ripeterli con migrazioni reali, usare **esclusivamente un database PostgreSQL
di test dedicato**: i test eliminano utenti, slot e appuntamenti di prova.

```powershell
.\mvnw.cmd test "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/nutritional_expert_test" "-Dspring.datasource.username=TEST_USER" "-Dspring.datasource.password=TEST_PASSWORD" "-Dspring.flyway.enabled=true" "-Dspring.jpa.hibernate.ddl-auto=validate"
```

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

I test Angular coprono form, accesso alla pagina protetta, registrazione riuscita,
errori di login, ripristino/scadenza della sessione e logout. Per la modifica profilo
verificano precompilazione, annullamento, salvataggio, errori e risposte successive
al logout. I test backend verificano persistenza, aggiornamenti parziali, rimozione
del telefono, validazione e isolamento tra utenti, preservando credenziali e ruolo.

Disponibilità e prenotazioni sono coperte da test per permessi, date, sovrapposizioni,
versioni obsolete, prenotazioni simultanee, conflitto utente/ADMIN, riutilizzo dopo
annullamento e isolamento degli elenchi personali. Il test del vincolo PostgreSQL
contro sovrapposizioni simultanee è saltato su H2: eseguire la suite PostgreSQL per
verificare anche quel caso e le migrazioni. Il frontend verifica creazione slot,
guard ADMIN, invio prenotazione, selezione obbligatoria, conflitti, errori e sessione scaduta.
La gestione degli stati include test per autorizzazioni, filtri, vincoli temporali,
stati definitivi, conflitti tra ADMIN, annullamento con riattivazione e nuova prenotazione,
visibilità dello stato nell'elenco personale e conferma delle azioni nell'interfaccia.
