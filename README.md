# Application de gestion des archives — PFE

Application web full-stack pour la gestion des archives physiques : bordereaux de transfert, boîtes, règles de conservation, emplacements (épis / blocs) et centre d'alertes.

## Stack technique

| Couche | Technologies |
|--------|----------------|
| **Frontend** | Angular 21, PrimeNG, Transloco (FR / AR, RTL) |
| **Backend** | Spring Boot 3.5, Spring Security, JPA |
| **Base de données** | Oracle (dev) — H2 en tests unitaires |
| **Auth** | Sessions serveur + cookie `httpOnly` (`USER_SESSIONS`) |

## Structure du dépôt

```
MY-F-VERSION/
├── BACK/     API REST (Spring Boot, port 8081)
├── FRONT/    Interface Angular (port 4200)
└── README.md
```

## Prérequis

- **Java 17+** et **Maven 3.9+**
- **Node.js 20+** et **npm**
- **Oracle Database** (XE / XEPDB1 ou équivalent)

## Installation et démarrage

### 1. Backend

```bash
cd BACK

# Créer votre fichier de configuration locale (non versionné) :
# BACK/src/main/resources/application.properties
# Indiquer : URL Oracle, username, password, app.jwt.secret, CORS

mvn spring-boot:run
```

L'API est disponible sur `http://localhost:8081`.

### 2. Frontend

```bash
cd FRONT
npm install
npm start
```

L'application est disponible sur `http://localhost:4202` (`apiUrl` dans `src/environments/environment.ts`).

## Comptes utilisateurs

Comptes dans la table `USERS` (mot de passe **BCrypt**).

- **Administrateur** : `ROLE_ADMIN`
- **Agent** : `ROLE_USER` — lié à `USER_DETAILS` via le matricule

## Fonctionnalités principales

- Bordereaux, validation agent, affectation emplacements
- Boîtes, dossiers, règles de conservation
- Emplacements (épis, import Excel, alertes capacité)
- Centre d'alertes, dashboard, audit, notifications
- i18n français / arabe

## Tests & build

```bash
cd BACK && mvn test
cd FRONT && npm run build
```

## Configuration

`BACK/src/main/resources/application.properties` est **ignoré par Git** 

