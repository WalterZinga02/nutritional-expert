# Nutritional Expert

Piattaforma web per la gestione degli utenti e degli appuntamenti di uno studio di nutrizione.

## Struttura

- `backend`: API REST Spring Boot, Java 17 e Maven Wrapper.
- `frontend`: Single Page Application Angular.

## Prerequisiti

- Java 17
- Node.js 24 LTS
- PostgreSQL 17

Le credenziali locali non devono essere aggiunte a Git. Copiare `.env.example` in `.env` e sostituire i valori esclusivamente nel file locale.

## Comandi

Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd frontend
npm start
```
