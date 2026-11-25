# Arlette Debo — Backend (monolithe modulaire)

API de la plateforme interne (salons SKY · KIDS, coin boutique par salon). Elle applique la **matrice des profils & fonctionnalités**
(`Matrice_profils_fonctionnalites_plateforme AD.xlsx`) et ses **règles de gestion**.

**Stack :**
- Java 21, avec les threads virtuels activés.
- Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3, Tomcat 11).
- Spring Security et JWT via le resource server OAuth2 (Nimbus).
- PostgreSQL 16 et Flyway 12.
- Spring Modulith pour vérifier l'architecture.

## Démarrage local

```bash
# 1. Base de données dans Docker (PostgreSQL 16) — conteneur arlette-debo-db, port 5432
docker compose -f docker-compose.dev.yml up -d
# 2. API (profil dev par défaut : seeder actif, Swagger exposé)
mvn spring-boot:run
```

- **Bases (conteneur `arlette-debo-db`) :** `arlette_debo` (dev) et `arlette_debo_test` (tests), utilisateur `arlette` / `arlette_dev`.
  Les données sont dans le volume `arlette-debo-dev_pgdata-dev`. Console : `docker exec -it arlette-debo-db psql -U arlette -d arlette_debo`.
- **Repartir de zéro :** `docker compose -f docker-compose.dev.yml down -v` puis `up -d` (Flyway et les seeders recréent tout).

- **API :** http://localhost:8080
- **Swagger :** http://localhost:8080/swagger-ui.html (profil dev uniquement)
  - **Se connecter :** bouton **Authorize** → bloc *connexion* : e-mail + mot de passe (ex. `dg@yopmail.com` / `Arlette@2026`),
    client_id/secret vides. Ou bloc *bearer* : coller un `accessToken`. Le jeton est conservé au rechargement (15 min).
  - **Groupes** (liste en haut à droite) : toute l'API · accès & habilitations · pilotage · opérations salons · fonctions support.
  - Chaque route documente ses erreurs (400/401/403/404/409) au format Problem Details avec le `code` métier.
  - **Production :** `app.security.expose-api-docs=false` → springdoc désactivé, aucune route de documentation exposée.
- **Tests :** `mvn test`, sur la base `arlette_debo_test`, recréée à chaque exécution.

### Comptes créés par le seeder (un par profil)

Le mot de passe de développement est `Arlette@2026`, défini par `SEED_PASSWORD`.

| Profil | E-mail |
|---|---|
| DG | dg@yopmail.com |
| DO | do@yopmail.com |
| Manager | manager@yopmail.com |
| Coordination | coordination@yopmail.com |
| Caissier | caissier@yopmail.com |
| Chargé clientèle | clientele@yopmail.com |
| Coiffeur | coiffeur@yopmail.com |
| Boutique | boutique@yopmail.com |
| Inventaire | inventaire@yopmail.com |
| Community | community@yopmail.com |
| Administration | administration@yopmail.com |

Le seeder est idempotent. Il crée aussi :
- les deux relais : « Relais SKY », inactif tant que la formation n'est pas validée, et « Relais Manager » ;
- un dossier RH lié à chaque compte opérationnel.

## Architecture

Le code est un monolithe modulaire. Chaque package est un module, et `ModularityTests` vérifie l'architecture à chaque build.

```
sn.arlettedebo.platform
├── shared          noyau ouvert : entités de base (UUID v7, audit, @Version), codes Module/Action,
│                   erreurs Problem Details, événement d'audit
├── iam             identité, matrice, habilitations « * », relais, JWT, comptes        ← API : AccessPolicy, CurrentUser, UserDirectory
├── audit           journal append-only alimenté par événements (même transaction)
├── referentiel     catalogues tp_ et paramètres (seuil DG…)                            ← API : Referentiels, Parametres
├── rh              dossiers, compétences, formations, absences, pointage, paie, évaluations
├── finance         dépenses : demande → validation → paiement → contrôle, justificatifs
├── pilotage        tableau de bord, reporting (évolution vs période précédente, CSV), « À traiter »
├── planning        rendez-vous, créneaux                                    ← API : consultation
├── qualite         contrôles, NC, actions, réclamations, avis, signalements, hygiène, maintenance ← consultation
├── caisse          sessions de caisse, encaissements                        ← consultation, contrôle des clôtures
├── commercial      clientes (CRM), abonnements, demandes d'autorisation     ← demande / arbitrage DG-DO
├── stock           articles, mouvements, comptages, commandes               ← consultation, validation des commandes
└── communication   publications, notes internes, événements                 ← circuit complet de validation
```

**Règles entre modules :**
- un module n'utilise que l'API publique d'un autre (types du package racine) ;
- les références inter-modules passent par UUID ;
- l'audit est découplé par événements.

## Pilotage (profil DG)

Chaque module publie ses indicateurs (`SourceIndicateurs`) et ses tâches en attente (`SourceDeTaches`) ; le module
`pilotage` les agrège selon les droits de l'utilisateur, sans dépendre des modules métier.

| Route | Rôle |
|---|---|
| `GET /api/v1/pilotage/tableau-de-bord` | Indicateurs par module + comparatif des salons (dashboard G) |
| `GET /api/v1/pilotage/reporting` | Reporting d'une période, évolution vs période précédente, tendance favorable/défavorable |
| `GET /api/v1/pilotage/reporting.csv` | Export Excel (BOM UTF-8, `;`, protection contre l'injection de formules) |
| `GET /api/v1/pilotage/a-traiter` | Validations et contrôles qui attendent l'utilisateur, triés par enjeu |
| `POST /api/v1/commercial/autorisations/{id}/decision` | Arbitrage des remises / gratuités / remboursements (caisse V) |
| `POST /api/v1/stock/commandes/{id}/decision` | Validation des commandes fournisseurs (stocks V) |
| `POST /api/v1/communication/publications/{id}/etapes` | Validation des publications après vérification DO (communication V) |
| `POST /api/v1/caisse/sessions/{id}/controle` | Contrôle indépendant des clôtures de caisse |
| `POST /api/v1/finance/depenses/{id}/validation` | Validation des dépenses, sans plafond (administration V) |

Le tableau de bord s'adapte à la restriction du profil : « administratif » (sans planning ni qualité),
« statistiques » (communication et produits), « personnel » (planning de la coiffeuse).

## Sécurité

| Mesure | Détail |
|---|---|
| Jeton d'accès | JWT HS256, 15 min. Émetteur, audience, dates et signature sont vérifiés. Il porte des claims minimaux (`sub`, `ver`, `profil`). |
| Droits | Ils ne sont **pas** dans le JWT : ils sont recalculés depuis la base et mis en cache Caffeine. Une suspension par la DG s'applique **à la requête suivante**. |
| Révocation | Claim `ver` comparé à `td_utilisateur.version_jeton`. Une déconnexion globale, un changement de mot de passe ou une désactivation invalide tous les JWT. |
| Jeton de rafraîchissement | Opaque (256 bits), stocké **haché** (SHA-256) et à usage unique. Il est rotatif : un **rejeu** révoque toute la session et les JWT. |
| Transport du refresh | Cookie `HttpOnly; SameSite=Strict; Path=/api/v1/auth`, avec `Secure` en production. Les clients natifs passent par `X-Client: native`. |
| Mots de passe | BCrypt coût 12, politique de complexité, mots de passe provisoires à changer. |
| Force brute | Verrouillage après 5 échecs pendant 15 min, plus une limite par IP (30/min). Le temps de réponse est constant pour un e-mail inconnu. |
| Autorisations | `@PreAuthorize("hasAuthority('administration:V')")` + règles dépendant des données (périmètre, profil, restriction « limité »). |
| En-têtes | CSP, HSTS, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, CORS strict. |
| Erreurs | Problem Details (RFC 9457) avec un `code` stable, jamais de trace technique. |
| Fichiers | Type vérifié par signature binaire (PDF, PNG, JPEG), nom généré, empreinte SHA-256. |

## Base de données

**Conventions :**
- `tp_` : tables de référence et de paramétrage ;
- `td_` : tables de données dynamiques ;
- `tr_` : tables relationnelles.

Le schéma est géré **exclusivement par Flyway** (`ddl-auto=validate`).

Les règles de gestion sont garanties **aussi par la base** (défense en profondeur) :
- `ck_td_depense_*` : séparation demande, validation, paiement et contrôle ;
- `ck_td_session_caisse_controle_independant` : contrôle de caisse indépendant ;
- `ck_td_comptage_separation` : la personne qui compte ne corrige pas son propre écart ;
- `ck_td_demande_autorisation_separation` et `ck_td_publication_separation` : pas d'auto-validation ;
- `ck_td_delegation_formation` : un relais n'est actif qu'après formation ;
- `ex_td_rendez_vous_coiffeuse` (EXCLUDE + GiST) : pas de double réservation d'une coiffeuse.

**Index :**
- B-tree composites alignés sur les requêtes ;
- **index partiels** pour les files de travail (« à contrôler », « en attente », alertes stock) ;
- **BRIN** sur les journaux en ajout seul ;
- **GiST** sur les périodes d'absence ;
- **trigrammes** (`pg_trgm`) pour la recherche de noms ;
- e-mails en **CITEXT**.

La politique d'indexation des clés étrangères est décrite dans `V9__index_complementaires.sql`, et un test la vérifie.

Identifiants : UUID v7, ordonnés dans le temps, ce qui évite la fragmentation des index et les rend non devinables.

## Production

```bash
cp .env.example .env    # DB_PASSWORD, JWT_SECRET et POINTAGE_SECRET (openssl rand -base64 48), CORS_ORIGINS
docker compose up -d --build
```

Le profil `prod` refuse de démarrer sans secrets. Swagger y est désactivé, et le seeder aussi.

## État d'avancement

**Fait (backend + front branché, testé de bout en bout) :**
- **Socle :** 62 tables `tp_`/`td_`/`tr_` + migrations V10–V12 (index, téléphones normalisés, anti double réservation sur RDV actifs).
- **Sécurité :** JWT, rafraîchissement par cookie HttpOnly protégé contre le CSRF, matrice C/S/G/V, Swagger (dev).
- **Lot DG :** pilotage, à traiter, arbitrages, reporting, journal, habilitations, dépenses.
- **Lot Manager + Caisse SKY :**
  - Caisse : ouverture, encaissements (cliente, coiffeuse, geste commercial autorisé), annulation motivée, clôture, contrôle indépendant.
  - Clientèle : prospects, clientes, abonnements (vente, séances).
  - Planning : prise de RDV, statuts, déplacement, réaffectation, créneaux de réservation.
  - Qualité : contrôles (⇒ NC), NC et actions correctives, réclamations, avis, signalements, hygiène, incidents.
  - RH : dossiers, absences et congés, variables de paie, évaluations.
- **Lot Boutique / Inventaires :** catalogue (références générées), mouvements tracés (entrée, sortie, perte, casse,
  transfert), inventaire à l'aveugle avec écart traité par une autre personne (ajustement tracé), commandes
  fournisseurs (besoin → validation DO/DG → passation → réception = entrées en stock), vente boutique atomique
  (sorties de stock + encaissement dans la caisse du jour). Migration V13.
- **Tests :** 99 tests d'intégration backend, 65 tests unitaires front, scénarios navigateur par profil.

- **Communication :** calendrier éditorial (format, canal, cible), modification par l'auteur, circuit vérification DO →
  validation DG → publication → analyse (portée, interactions, clics, abonnés). Migration V14.

- **Pointage QR :** la borne du salon (Manager, accueil, RH) affiche un QR signé HMAC-SHA256 par le serveur
  (clé `POINTAGE_SECRET`, jamais envoyée au navigateur), lié au salon et renouvelé à chaque rotation (paramètre DG,
  30–300 s). Le personnel le scanne depuis son compte : arrivée, pause, reprise, départ ; retard calculé selon la
  tolérance. QR falsifié, expiré ou d'un autre salon refusé ; aucun pointage sans QR.

- **Réservation en ligne (publique, sans compte) :** `/api/v1/public/reservation` — prestations, créneaux ouverts
  par l'accueil avec places restantes, réservation (nom + téléphone). Capacité vérifiée sous verrou, horizon 60 jours,
  1 h minimum avant le créneau, 2 rendez-vous à venir par numéro, 20 réservations / heure / IP. Le rendez-vous arrive
  « prévu », source EN_LIGNE, sans coiffeuse, avec une référence courte ; il apparaît dans « À traiter » de l'accueil,
  qui le confirme et l'affecte. Il n'y a pas d'espace cliente : les clientes ne créent pas de compte.
  En dev, le seeder ouvre des créneaux sur 14 jours glissants (lundi–samedi).
