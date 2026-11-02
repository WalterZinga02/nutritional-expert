# Nutritional Expert

Piattaforma web per i clienti di uno studio di nutrizione.

## Stato attuale

Registrazione e login collegati tra Angular e Spring Boot, visualizzazione e modifica del profilo personale e logout.
Homepage dello studio, prenotazioni, recensioni e gestione ADMIN sono i prossimi moduli.

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

## Verifiche

```powershell
cd backend
.\mvnw.cmd test
```

I test predefiniti usano H2 in memoria, senza credenziali locali, e non eseguono
le migrazioni PostgreSQL. Coprono registrazione, hashing, login, duplicati,
registrazioni concorrenti, validazione, autorizzazione e token non validi/scaduti.

Per ripeterli con migrazioni reali, usare **esclusivamente un database PostgreSQL
di test dedicato**: i test eliminano gli utenti all'inizio di ogni caso.

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
