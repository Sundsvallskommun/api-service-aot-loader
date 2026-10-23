# api-service-aot-loader

_Migrates the current alcohol and tobacco permits, the notifications and the inspection errands from the old systems
(AlkT, and AlkOL2 with the sales of tobacco, folköl, e-cigarettes and tobacco-free nicotine products) to SupportManagement and PartyAssets._

Every permit and notification becomes a closed errand in the namespace AOT and a post in PartyAssets, linked to the
errand. The files of the source go on the errand, and the permit certificate, the annex, the drawings and the decisions
of a permit also go on its post, each in its category (decisions 34–37). Every inspection errand becomes a closed errand, related to the permit errand of its place when it concerned the
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

The log never holds a request or a response: they hold names and personal identity numbers (K6). Whatever the log
levels say, only the migration's own loggers write below INFO; the DEBUG and TRACE of the libraries, which show whole
requests, are filtered out.

## Configuration

### The .env file

The settings that differ between environments and computers, such as URLs, server names and paths, go in a file `.env`
in the root of the repository. The repository is public, so they must never be written anywhere else in it. Git ignores
`.env`.

**Keys and passwords never go in `.env`, or in any other file (section 12 of the solution document).** The only secret
is `AOT_LOADER_CLIENT_SECRET`, the secret of the client in the API gateway; it is typed in each time the service is
started, as shown under [Run](#run). The source databases are read with your own Windows account, so they have no
password. The account may only read them: before a run the service checks that it cannot change anything in them, and
a real run does not start when it can (section 9 of the solution document).

1. Copy the template: `Copy-Item .env.example .env`
2. Fill in the values: one `NAME=value` per line, without quotes. Lines starting with `#` are comments.
3. Check that git ignores the file: `git check-ignore -v .env` must name `.gitignore`, and `git status` must not list
   `.env`.
4. Use one `.env` per environment, e.g. by keeping `.env` for test and copying in the values for production only when
   that run is made. The local database must also differ between environments (`AOT_LOADER_DB_URL`, see
   [The local database](#the-local-database)).

The client in the API gateway needs subscriptions to SupportManagement, PartyAssets, Party and the relation service.
When the migration is approved, the production secret is changed or the client is closed (section 12).

|                                 Variable                                  |                                                                                                               Content                                                                                                                |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AOT_LOADER_CLIENT_ID`                                                    | The client of the API gateway                                                                                                                                                                                                        |
| `AOT_LOADER_CLIENT_SECRET`                                                | The secret of the client. **Typed in at start, never in `.env`.**                                                                                                                                                                    |
| `AOT_LOADER_TOKEN_URI`                                                    | The token endpoint of the API gateway                                                                                                                                                                                                |
| `AOT_LOADER_DECISION_AD_ACCOUNT`                                          | The AD account the decisions of the permits are written with, the account name alone. Default `migration-job`. SupportManagement takes a manual decision only from an AD account; a real run with permits does not start without one |
| `SUPPORT_MANAGEMENT_URL`, `PARTY_ASSETS_URL`, `PARTY_URL`, `RELATION_URL` | The APIs, through the gateway                                                                                                                                                                                                        |
| `ALKT_DB_URL`, `ALKOL_DB_URL`                                             | The source databases, e.g. `jdbc:sqlserver://<server>:1433;databaseName=<database>;integratedSecurity=true;trustServerCertificate=true`                                                                                              |
| `ALKT_DOCUMENTS_DIR`, `ALKT_ATTACHMENTS_DIR`                              | The directories of AlkT on the file area (`Dokument` and `Bilder`)                                                                                                                                                                   |
| `ALKOL_DOCUMENTS_DIR`, `ALKOL_ATTACHMENTS_DIR`                            | The directory of AlkOL on the file area, the same for both                                                                                                                                                                           |
| `AOT_LOADER_DB_URL`                                                       | The local database, see below. Default `jdbc:h2:file:./data/aot-loader`                                                                                                                                                              |
| `AOT_LOADER_RESULT_DIRECTORY`                                             | Where the report of each run is written as a file, see below. Default `./data/resultat`; blank writes none                                                                                                                           |

The connection to SQL Server is encrypted. The server presents only the certificate it makes itself
(`SSL_Self_Signed_Fallback`), which no truststore can trust, so the URLs end with `trustServerCertificate=true` and the
certificate is not verified (decision 33). When the server gets a certificate from an issuer, that setting is removed.

### Settings in application.yml

The settings that are the same everywhere are in `application.yml` under `aot-loader`:

- **Errand types:** everything an errand type becomes in the target systems, under `errand-types`:

  - the classification (decision 5);
  - the labels by their resource path (section 7.3.4): `labels` for every errand of the type, `subtype-labels` for
    the subtypes of a serving permit, and `source-errand-types` for the errand types in the source that are
    inspection errands, with the label of each (section 7.7);
  - for inspection errands and visits, `only-places-with-permit`: only those of places with a current permit (section 7.7,
    decision 29). A sales place in AlkOL marked as ceased gets none, whatever the setting says (decision 31);
  - the type and title of the post in PartyAssets (decisions 4 and 20). The types are those pw-alkt gives the permits
    it creates, e.g. `AlcoholServingPermit`.

  The label "Migrerat ärende" is not a setting: every migrated errand gets it, since it blocks every process
  (decision 17).

- **Municipalities:** every errand is created in the municipality the run is started for, e.g. 2281 in
  `POST /2281/migrations/runs`. The errand and its post in PartyAssets get the parameter `premisesMunicipalityId` with
  the municipality code of the place: that of its area when the area is listed under `municipalities`, e.g. 2262 for
  Timrå, and otherwise the municipality of the run (decision 19). An item is never moved to another municipality once
  it has been written.

- **Status:** `errand-status`, the closed status of the migrated errands: `SOLVED`, the one AOT has (decision 5), and
  `errand-resolution`, `CLOSED`, the one Draken closes an errand of AOT with.

- **Cleanup:** `cleanup.enabled` turns on the calls that remove what the migration wrote (section 10.3). It is `false`,
  and is turned on only for a test environment, e.g. with `AOT_LOADER_CLEANUP_ENABLED=true` in `.env`. Never in
  production.

- **Roles:** the permit holder is `PRIMARY`, and the persons with significant influence are `CONTACT` with the parameter
  `PBI` = `true` (decision 15). The holder's contact person is a `CONTACT` of its own, without `PBI`.
  `inspection-holder` is the role of the holder an inspection errand or a documentation errand concerned: `CONTACT`,
  since with `PRIMARY` the errand would show on Mina sidor (decisions 27 and 71). An inspection visit has no holder
  (section 7.7.2).

A setting left blank stops a real run, and a dry run lists it.

### The local database

The status table, the exception list and the reports of the runs are kept in an H2 database, a single file:
`data/aot-loader.mv.db` under the directory the service is started from. It survives restarts, and it is what lets a
later run continue where an earlier one stopped. It holds no names or numbers of permit holders or other persons.

- Use an absolute path and one file per environment, e.g. `AOT_LOADER_DB_URL=jdbc:h2:file:C:/migrering/aot-loader-test`.
  The first run binds the file to the SupportManagement and namespace, the PartyAssets and the relation service it
  calls, and a run against another environment is refused.
- Open it with DBeaver (H2 driver) while the service is stopped; H2 locks the file while it runs.
- Copy the file, with the service stopped, to keep it as a record of the migration.

### The result file

When a run finishes, dry or real, with rows in its report, its whole report is written to `data/resultat` as a CSV file
for Excel, the same as `GET /2281/migrations/runs/{runId}/report.csv` gives: one row per item, with the errands that
were not migrated and why, and the files that were not uploaded (G14) with their names and the reason, e.g. that a
file is larger than 50 MB.
That file is what is sent to the business. Its name tells when the run started, what it was and the start of its id,
e.g. `rapport-20261022-153000-torrkorning-1a2b3c4d.csv`, and the message of the run says where it is. A file that
cannot be written is told in the message of the run; the run itself is not affected. Like the local database, the file
holds no numbers of permit holders, but it does hold the names of places and files.

The protection against duplicates does not rest on the local database alone: before anything is created the target
systems are searched, the errand on the external tag `migreringsid`, drafts included, the post in PartyAssets on its
asset id and partyId, and the relation on its source and target.

#### Moving the exception list to production

The file for production starts without the exception list. What the business decided is about the items of the sources,
which are the same for every environment, so the list is moved with the API: saved while the service runs on the file
for test, and put back once it runs on the file for production.

```powershell
$url = 'http://localhost:8080/2281/migrations/exceptions'
# With the service running on the file for test. The response is saved as it is, since Windows PowerShell 5.1 would
# read å, ä and ö in it as ISO-8859-1. Git ignores the folder data.
New-Item -ItemType Directory -Force data | Out-Null
Invoke-WebRequest $url -UseBasicParsing -OutFile data/exceptions.json
# With the service running on the file for production
foreach ($e in (Get-Content data/exceptions.json -Raw -Encoding UTF8 | ConvertFrom-Json)) {
    # Kept as a date, whether PowerShell reads it as text or as a DateTime
    $decidedAt = if ($e.decidedAt -is [datetime]) { $e.decidedAt.ToString('yyyy-MM-dd') } else { $e.decidedAt }
    $body = @{ outcome = $e.outcome; decidedBy = $e.decidedBy; decidedAt = $decidedAt; comment = $e.comment } | ConvertTo-Json
    Invoke-RestMethod -Method Put "$url/$($e.source)/$($e.kind)/$($e.sourceId)" -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
}
[Text.Encoding]::UTF8.GetString((Invoke-WebRequest $url -UseBasicParsing).RawContentStream.ToArray()) -eq (Get-Content data/exceptions.json -Raw -Encoding UTF8)
```

The last line prints `True` when the list for production is the same as the one saved. Moving it again changes
nothing: an item already on the list gets what the saved list says.

## API

Swagger UI: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

|                                  Call                                   |                                                                                                                                                               What it does                                                                                                                                                                |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /2281/migrations/runs`                                            | Starts a run in the background. A dry run unless the body says `"dryRun": false`. The body may narrow it: `sources` (`ALKT`, `ALKOL`), `kinds` (`TILLSTAND`, `ANMALAN`, `TILLSYN`, `TILLSYNSBESOK`, `DOKUMENTATION`) and `limit`. A field it does not know, or an empty list, is refused with 400                                         |
| `GET /2281/migrations/runs/{runId}`                                     | The status of the run, with the number of items per outcome                                                                                                                                                                                                                                                                               |
| `POST /2281/migrations/runs/{runId}/stop`                               | Stops the run before its next item                                                                                                                                                                                                                                                                                                        |
| `GET /2281/migrations/runs/{runId}/report`                              | The report: one row per item, with the review rules and what went wrong                                                                                                                                                                                                                                                                   |
| `GET /2281/migrations/runs/{runId}/report.csv`                          | The same as a CSV file for Excel; `?outcome=FEL&outcome=GRANSKAS` lists only those                                                                                                                                                                                                                                                        |
| `POST /2281/migrations/items/{source}/{kind}/{id}?dryRun=false`         | Migrates a single item and waits for it                                                                                                                                                                                                                                                                                                   |
| `GET /2281/migrations/items`                                            | The status table                                                                                                                                                                                                                                                                                                                          |
| `PUT`, `DELETE /2281/migrations/exceptions/{source}/{kind}/{id}`        | The exception list (section 7.1.1)                                                                                                                                                                                                                                                                                                        |
| `POST /2281/migrations/cleanups`                                        | Only with `cleanup.enabled`. Starts a cleanup in the background, followed and stopped as a run: removes the relations, the post in PartyAssets and the errand the migration wrote for each item of the status table in the selection, and the item. A dry run unless the body says `"dryRun": false`; the body may narrow it as for a run |
| `POST /2281/migrations/items/{source}/{kind}/{id}/cleanup?dryRun=false` | Only with `cleanup.enabled`. Cleans up a single item and waits for it                                                                                                                                                                                                                                                                     |

The id of an item is `ObjektID` in AlkT or `FSID` in AlkOL for a permit, `FSID` and product for a
notification (`1234-FOLKOL`, `1234-ECIGARETTER`, `1234-TOBAKSFRI_NIKOTIN`), `ArendeID` for an inspection errand, and
`TillsynsTillfalleNr` for an inspection visit.

Only one run at a time: a second call while a run is running gets 409. Starting a run again never migrates an item
twice; an item already migrated is left as it is, and an item that stopped half way continues from where it stopped.
Its post in PartyAssets is then brought up to date: it gets the status the item is to have now, ACTIVE or EXPIRED, since
the exception list may have changed in between, and the title, last day and parameters the source gives now. PartyAssets
changes those only while the post is ACTIVE, and never its type, issue date, description or party; the report lists
what could not be changed. A file already on the errand or the post, with the same name and content, is not uploaded
again.

### Example

```bash
# A dry run of everything
curl -X POST http://localhost:8080/2281/migrations/runs -H 'Content-Type: application/json' -d '{}'

# The rows that need looking at, for the business
curl -o rapport.csv 'http://localhost:8080/2281/migrations/runs/<runId>/report.csv?outcome=FEL&outcome=GRANSKAS'

# A real run of the permits in AlkT, ten at a time
curl -X POST http://localhost:8080/2281/migrations/runs -H 'Content-Type: application/json' \
  -d '{"dryRun": false, "sources": ["ALKT"], "kinds": ["TILLSTAND"], "limit": 10}'

# In a test environment with cleanup.enabled: what a cleanup of everything would remove, and then removing it
curl -X POST http://localhost:8080/2281/migrations/cleanups -H 'Content-Type: application/json' -d '{}'
curl -X POST http://localhost:8080/2281/migrations/cleanups -H 'Content-Type: application/json' -d '{"dryRun": false}'
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
