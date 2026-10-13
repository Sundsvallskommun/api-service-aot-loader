# api-service-aot-loader

_Migrates the current alcohol and tobacco permits, the notifications and the inspection errands from the old systems
(AlkT, and AlkOL2 with the sales of tobacco, folköl, e-cigarettes and tobacco-free nicotine products) to SupportManagement and PartyAssets._

Every permit and notification becomes a closed errand in the namespace ALKT and a post in PartyAssets, linked to the
errand. Every inspection errand becomes a closed errand, related to the permit errand of its place when it concerned the
same permit holder. The migrated errands carry the label "Migrerat ärende", which keeps every process away from them. No
mail, SMS or message is sent, and no process is started. The design, the mapping and the decisions are in
[the solution document](docs/losningsdokument-migrering.md).

The service runs on a developer computer (decision 10). It only reads the source databases and the file area, and it
writes to the target systems through their APIs only.

## Getting started

### Prerequisites

- Java 25 and Maven
- VPN access to the SQL Server of the source databases, to the API gateway, and read access to the file area
- A Windows account with read access only (`CONNECT` and `SELECT`) to both source databases
- Podman or Docker, for the integration tests only

### Build

```bash
mvn clean verify
```

On Windows the build copies the native library of the SQL Server driver, `mssql-jdbc_auth-<version>.x64.dll`, to
`target/native`. It is needed for Windows authentication, and its version must be exactly the version of the driver;
the property `mssql-jdbc.base-version` in `pom.xml` sets both.

### Run

1. Set up `.env` once, as described in [The .env file](#the-env-file).
2. Open a new PowerShell window in the root of the repository, load `.env` and type in the client secret:

```powershell
# Load .env into this window. Blank values and comments are skipped.
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and $Matches[2]) { Set-Item "env:$($Matches[1])" $Matches[2] }
}
# The secret is typed in, never stored: it is not shown, not saved in the history, and gone when the window closes
$env:AOT_LOADER_CLIENT_SECRET = [Net.NetworkCredential]::new('', (Read-Host 'Client secret' -AsSecureString)).Password
mvn spring-boot:run
```

`spring-boot:run` starts the JVM with `-Djava.library.path=target/native`. Started any other way, pass that option
yourself. The service listens on `127.0.0.1:8080` only. Close the window when the run is done; the variables, the
secret included, live only in it.

## Configuration

### The .env file

The settings that differ between environments and computers, such as URLs, server names and paths, go in a file `.env`
in the root of the repository. The repository is public, so they must never be written anywhere else in it. Git ignores
`.env`.

**Keys and passwords never go in `.env`, or in any other file (section 12 of the solution document).** The only secret
is `AOT_LOADER_CLIENT_SECRET`, the secret of the client in the API gateway; it is typed in each time the service is
started, as shown under [Run](#run). The source databases are read with your own Windows account, so they have no
password.

1. Copy the template: `Copy-Item .env.example .env`
2. Fill in the values: one `NAME=value` per line, without quotes. Lines starting with `#` are comments.
3. Check that git ignores the file: `git check-ignore -v .env` must name `.gitignore`, and `git status` must not list
   `.env`.
4. Use one `.env` per environment, e.g. by keeping `.env` for test and copying in the values for production only when
   that run is made. The local database must also differ between environments (`AOT_LOADER_DB_URL`, see
   [The local database](#the-local-database)).

The client in the API gateway needs subscriptions to SupportManagement, PartyAssets, Party and the relation service.
When the migration is approved, the production secret is changed or the client is closed (section 12).

|                                 Variable                                  |                                                   Content                                                   |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `AOT_LOADER_CLIENT_ID`                                                    | The client of the API gateway                                                                               |
| `AOT_LOADER_CLIENT_SECRET`                                                | The secret of the client. **Typed in at start, never in `.env`.**                                           |
| `AOT_LOADER_TOKEN_URI`                                                    | The token endpoint of the API gateway                                                                       |
| `SUPPORT_MANAGEMENT_URL`, `PARTY_ASSETS_URL`, `PARTY_URL`, `RELATION_URL` | The APIs, through the gateway                                                                               |
| `ALKT_DB_URL`, `ALKOL_DB_URL`                                             | The source databases, e.g. `jdbc:sqlserver://<server>:1433;databaseName=<database>;integratedSecurity=true` |
| `ALKT_DOCUMENTS_DIR`, `ALKT_ATTACHMENTS_DIR`                              | The directories of AlkT on the file area (`Dokument` and `Bilder`)                                          |
| `ALKOL_DOCUMENTS_DIR`, `ALKOL_ATTACHMENTS_DIR`                            | The directory of AlkOL on the file area, the same for both                                                  |
| `AOT_LOADER_DB_URL`                                                       | The local database, see below. Default `jdbc:h2:file:./data/aot-loader`                                     |
| `AOT_LOADER_ERRAND_STATUS`                                                | The closed status of the migrated errands (decision 5)                                                      |
| `AOT_LOADER_ROLE_INSPECTION_HOLDER`                                       | The role of the holder an inspection errand concerned (section 7.7)                                         |

The connection to SQL Server is encrypted and the certificate of the server is verified; if Java does not trust it,
add it to a truststore. `trustServerCertificate` is not used.

### Settings in application.yml

The settings that are the same everywhere are in `application.yml` under `aot-loader`:

- **Errand types:** the classification of each type (decision 5), and the type and title of the post in PartyAssets
  (decisions 4 and 20). The types are those pw-alkt gives the permits it creates, e.g. `AlcoholServingPermit`.
- **Municipalities:** an errand belongs to the municipality the run is started for, e.g. 2281 in
  `POST /2281/migrations/runs`, unless the area of its place is listed under `municipalities` (decision 19). The places
  in the area Timrå go to Timrå, 2262. Every call for such an errand goes to that municipality, and the status table
  keeps it; an item is never moved to another municipality once it has been written.
- **Roles:** the permit holder is `PRIMARY`, and the persons with significant influence are `CONTACT` with the parameter
  `PBI` = `true` (decision 15).
- **Inspection errands:** the labels of the inspection errand types (section 7.7).

A setting left blank stops a real run, and a dry run lists it.

### The local database

The status table, the exception list and the reports of the runs are kept in an H2 database, a single file:
`data/aot-loader.mv.db` under the directory the service is started from. It survives restarts, and it is what lets a
later run continue where an earlier one stopped. It holds no names or numbers of permit holders or other persons.

- Use an absolute path and one file per environment, e.g. `AOT_LOADER_DB_URL=jdbc:h2:file:C:/migrering/aot-loader-test`.
  The first run binds the file to the SupportManagement it calls, and a run against another environment is refused.
- Open it with DBeaver (H2 driver) while the service is stopped; H2 locks the file while it runs.
- Copy the file, with the service stopped, to keep it as a record of the migration.

The protection against duplicates does not rest on the local database alone: before anything is created the target
systems are searched, the errand on the external tag `migreringsid`, drafts included, the post in PartyAssets on its
asset id and partyId, and the relation on its source and target.

## API

Swagger UI: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

|                               Call                               |                                                                                          What it does                                                                                          |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /2281/migrations/runs`                                     | Starts a run in the background. A dry run unless the body says `"dryRun": false`. The body may narrow it: `sources` (`ALKT`, `ALKOL`), `kinds` (`TILLSTAND`, `ANMALAN`, `TILLSYN`) and `limit` |
| `GET /2281/migrations/runs/{runId}`                              | The status of the run, with the number of items per outcome                                                                                                                                    |
| `POST /2281/migrations/runs/{runId}/stop`                        | Stops the run before its next item                                                                                                                                                             |
| `GET /2281/migrations/runs/{runId}/report`                       | The report: one row per item, with the review rules and what went wrong                                                                                                                        |
| `GET /2281/migrations/runs/{runId}/report.csv`                   | The same as a CSV file for Excel; `?outcome=FEL&outcome=GRANSKAS` lists only those                                                                                                             |
| `POST /2281/migrations/items/{source}/{kind}/{id}?dryRun=false`  | Migrates a single item and waits for it                                                                                                                                                        |
| `GET /2281/migrations/items`                                     | The status table                                                                                                                                                                               |
| `PUT`, `DELETE /2281/migrations/exceptions/{source}/{kind}/{id}` | The exception list (section 7.1.1)                                                                                                                                                             |

The id of an item is `ObjektID` in AlkT or `FSID` in AlkOL for a permit, `FSID` and product for a
notification (`1234-FOLKOL`, `1234-ECIGARETTER`, `1234-TOBAKSFRI_NIKOTIN`), and `ArendeID` for an inspection errand.

Only one run at a time: a second call while a run is running gets 409. Starting a run again never migrates an item
twice; an item already migrated is left as it is, and an item that stopped half way continues from where it stopped.

### Example

```bash
# A dry run of everything
curl -X POST http://localhost:8080/2281/migrations/runs -H 'Content-Type: application/json' -d '{}'

# The rows that need looking at, for the business
curl -o rapport.csv 'http://localhost:8080/2281/migrations/runs/<runId>/report.csv?outcome=FEL&outcome=GRANSKAS'

# A real run of the permits in AlkT, ten at a time
curl -X POST http://localhost:8080/2281/migrations/runs -H 'Content-Type: application/json' \
  -d '{"dryRun": false, "sources": ["ALKT"], "kinds": ["TILLSTAND"], "limit": 10}'
```

## Tests

The integration tests run against a SQL Server in a container with the tables of both source databases and made up
rows, a file area of made up files, and the target systems in WireMock. With Podman:

```bash
DOCKER_HOST='npipe:////./pipe/podman-machine-default' mvn clean verify
```

## License

This project is licensed under the [MIT License](LICENSE).

## Code status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-aot-loader&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-aot-loader)

---

© 2026 Sundsvalls kommun
