# Lösningsdokument: Migrering av alkohol- och tobakstillstånd

|                             |                                                                                                                                                                             |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Status                      | Beslut 1–19 och 21–23 tagna. Beslut 20, typen i PartyAssets, är öppet. Tillsynsärendena återstår att analysera och mappa (avsnitt 7.7).                                     |
| Datum                       | 2026-10-06                                                                                                                                                                  |
| Författare                  | Andreas Carlsson                                                                                                                                                            |
| Beslutslista                | Sidan "Migrering av alkohol- och tobakstillstånd" (https://claude.ai/code/artifact/c5ef17f8-e470-4c6e-80f2-76fbb5e5fdf6)                                                    |
| Verksamhetens förberedelser | Listorna *Att hantera före migrering* för AlkT och för tobak, med granskningsfrågorna `granskningslista.sql` och `granskningslista_tobak.sql`, som verksamheten får separat |

## 1. Sammanfattning

Gällande alkohol- och tobakstillstånd, anmälningar och tillsynsärenden flyttas från de gamla systemen till SupportManagement och PartyAssets. Varje tillstånd och anmälan blir ett avslutat ärende i SupportManagement och en post i PartyAssets, och de två kopplas ihop. Tillsynsärendena blir avslutade ärenden i SupportManagement. Migrerade ärenden får samma etiketter som vanliga ärenden plus etiketten "Migrerat ärende". Den etiketten spärrar processer, vilket kräver en liten ändring i SupportManagement.

Under migreringen skickas inga mail, sms eller meddelanden, och inga processer startar. Inga processer får heller starta senare, när handläggare ändrar de migrerade ärendena.

Arbetet görs av tjänsten `api-service-aot-loader`, en fristående Java-applikation som körs på en utvecklardator. Den läser källdatabaserna direkt med ett konto som bara har läsrätt och skriver allt via API:erna. Inget skrivs direkt i målsystemens databaser.

Migreringen görs i två faser:

| Fas |                                        Innehåll                                         |                                                           Läge 2026-10-06                                                            |
|-----|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| 1   | 135 serveringstillstånd och tillsynsärendena ur AlkT                                    | Tillstånden är analyserade och mappade. Tillsynsärendena återstår att analysera och mappa (avsnitt 7.7).                             |
| 2   | 67 tobakstillstånd och 192 anmälningar ur tobaksdatabasen, och dokument från en filarea | Tobaksdatabasen är analyserad och mappad (avsnitt 3.4 och 7.8). Filarean är kartlagd (avsnitt 3.5); matchningen av filerna återstår. |

Beslut 1–18 togs 2026-10-05. Analysen av tobaksdatabasen gav fem nya beslut, och 19, 21, 22 och 23 togs 2026-10-06. Beslut 20 är öppet: tillstånd och anmälningar får en annan typ än PERMIT i PartyAssets, för både alkohol och tobak, men vilken är inte bestämt. Alla beslut står i avsnitt 14. Det verksamheten behöver rätta eller avsluta före migreringen står i listorna *Att hantera före migrering* för AlkT och för tobak, som verksamheten får separat.

## 2. Mål och avgränsning

### 2.1 Mål

Efter migreringen gäller följande:

- Varje gällande tillstånd finns som ett ärende i namespace ALKT (kommun 2281) som handläggarna kan söka fram och läsa. Ärendet innehåller tillståndets uppgifter och är märkt som migrerat.
- Varje gällande tillstånd finns i PartyAssets, tillhör tillståndshavaren och är kopplat till ärendet.
- Varje tillsynsärende som ingår finns som ett avslutat, migrerat ärende, kopplat till serveringsstället.
- Varje anmälan i tobaksdatabasen finns som ett avslutat, migrerat ärende, med tillståndshavaren och försäljningsstället som intressenter, och som en post i PartyAssets kopplad till ärendet.
- Ingen kund har fått något meddelande, ingen process har startat, och inga mail har skickats.

### 2.2 Krav

| Nr |                                                                                     Krav                                                                                      |
|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| K1 | Inga mail, sms eller andra meddelanden skickas, varken under eller efter migreringen.                                                                                         |
| K2 | Ingen process i pw-alkt startar, varken under migreringen eller när ett migrerat ärende ändras senare.                                                                        |
| K3 | Migreringen kan köras om utan att det blir dubbletter, även efter ett avbrott mitt i ett tillstånd.                                                                           |
| K4 | Det finns ett provkörningsläge som bara rapporterar vad som skulle skapas, utan att skriva något.                                                                             |
| K5 | Varje migrerat tillstånd och tillsynsärende går att spåra från källrad till ärende och tillstånd, och tillbaka.                                                               |
| K6 | Tillståndshavarens och andra personers namn, personnummer och organisationsnummer finns bara i källsystemen och målsystemen, aldrig i loggar, rapporter eller statustabeller. |
| K7 | Källsystemen, även filarean, ändras inte av migreringen. Filarean läses bara.                                                                                                 |
| K8 | Migrerade ärenden är märkta, så att handläggare ser att ärendet är migrerat och kan söka fram alla migrerade ärenden.                                                         |

### 2.3 Vad som ingår (beslut 1)

|                                Ingår                                |                      Ingår inte                       |
|---------------------------------------------------------------------|-------------------------------------------------------|
| Gällande serveringstillstånd i AlkT (fas 1)                         | Tidigare tillstånd (412 i AlkT)                       |
| Tillsynsärenden i AlkT (fas 1, avsnitt 7.7)                         | Övrig ärendehistorik                                  |
| Gällande tobakstillstånd och anmälningar (fas 2)                    | Restaurangrapporter (2 641), debitering och statistik |
| Tillståndshavare, serveringsställe, omfattning, tider och villkor   | Serveringsansvarig personal (beslut 15)               |
| Filerna från det ärende som utfärdade tillståndet (beslut 7, fas 2) | Övriga dokument; de bevaras i arkivet                 |
| Personer med betydande inflytande (beslut 15)                       | AlkT:s logg (375 000 rader)                           |
|                                                                     | Pågående ärenden i AlkT (beslut 16)                   |

**Anmälningar.** I AlkT finns anmälningar bara som ärenden, till exempel anmälan av cateringlokal och ändring av serveringsansvariga. I tobaksdatabasen finns anmälningar om folköl, e-cigaretter och tobaksfria nikotinprodukter, som markeringar på försäljningsställena. De migreras enligt 7.8.5.

Det som inte migreras bevaras enligt dokumenthanteringsplanen, till exempel genom leverans till e-arkiv.

## 3. Källsystem

### 3.1 AlkT

AlkT är en SQL Server 2022-databas (`AlkTSundsvall`, sorteringsordning Finnish_Swedish_CI_AS). Databasen har en äldre kompatibilitetsnivå, under 110, så frågor mot den kan inte använda funktioner som kräver en högre nivå, till exempel `TRY_CONVERT`. Databasen är 450 MB, men tabellerna använder bara cirka 65 MB; den största är loggtabellen med 50 MB. Applikationen läser databasen direkt med ett konto som bara har läsrätt. Ingen export görs.

Tabellerna som migreringen använder:

|          Tabell           |   Rader    |                                                       Innehåll                                                       |             Används till              |
|---------------------------|------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| `Gällande_Tillstånd`      | 135        | Gällande tillstånd, ett per serveringsställe: drycker, omfattning, tider, perioder, utfärdandedatum och diarienummer | Kärnan i både ärendet och tillståndet |
| `Objekt`                  | 354        | Serveringsställe: namn, adress, serveringsställenummer, typ, nuvarande ägare                                         | Serveringsställe och ägare            |
| `Ägare`                   | 542        | Tillståndshavare: organisationsnummer, namn, bolagstyp, adress, kontaktuppgifter                                     | Tillståndshavare                      |
| `ObjVillkor`              | 100        | Villkor på serveringsställen, med kod, fritext och giltighetsdatum                                                   | Villkor                               |
| `Ärende_Beslut`           | 2 212      | Beslut. Bara det beslut som tillståndet pekar på används.                                                            | Senaste beslut, som upplysning        |
| `Klartext` och `KodTyper` | 253 och 25 | Kodlistor                                                                                                            | Översätter koder till text            |
| `PBI_ägare`               | 1 164      | Personer med betydande inflytande                                                                                    | Bara om beslut 15 säger det           |

Datamodellen i korthet:

```
Ägare 1 ──── n Objekt 1 ──── 0..1 Gällande_Tillstånd ──── 0..1 Ärende_Beslut
                  │
                  └──── n ObjVillkor
```

`Gällande_Tillstånd` saknar egen nyckel men har exakt en rad per serveringsställe. Serveringsställets id (`ObjektID`) fungerar därför som tillståndets id.

### 3.2 Vad analysen visar

Analysen gjordes 2026-10-05 med tabellstrukturen och sammanräknade siffror. Inga rader med personuppgifter lästes. Analysunderlaget förvaras utanför repot.

- **Bara serveringstillstånd.** AlkT innehåller inga tobakstillstånd. Inget serveringsställe är markerat som försäljningsställe, och ingen ärende- eller beslutstyp gäller tobak.
- **Tillståndet är komplett i sig.** Bara 41 av 135 tillstånd pekar på ett beslut som finns kvar. 81 saknar beslut och 13 pekar på beslut som har tagits bort. Ärendet och tillståndet byggs därför från `Gällande_Tillstånd`, `Objekt`, `Ägare` och `ObjVillkor`, inte från ärende- och beslutshistoriken.
- **Utfärdandedatum finns på alla 135 tillstånd** och diarienummer på 126. Tillstånden är utfärdade mellan 2000 och 2026.
- **Omfattning:**
  - starköl 132, vin 129, sprit 115, andra jästa alkoholdrycker 114,
  - allmänheten 119, slutna sällskap 68, året runt 125,
  - uteservering 92, catering 22, rumsservering 6, minibar 4, provsmakning 3, egen kryddning 2, gårdsförsäljning 1, pausservering 1.
- **Serveringstider.** Det första tidsintervallet är ifyllt på 131 tillstånd, alltid i formatet `hh:mm`. Starttid 5 är tom på alla. Intervall 2–4 och 6–8 är inte analyserade.
- **Tillståndshavare.** Tillstånden hör till 124 ägarposter. Ett organisationsnummer finns på flera poster, så det är högst 123 olika tillståndshavare.
  - 105 aktiebolag, 9 föreningar, 6 enskilda firmor, 2 handelsbolag, 1 kommanditbolag och 1 kommun.
  - 118 har organisationsnummer och 6 har personnummer. Det stämmer med de 6 enskilda firmorna, men de två uppgifterna är inte korskörda.
  - Alla nummer har 10 siffror och nästan alla har bindestreck.
- **Villkor.** 67 serveringsställen har villkor, 100 villkor totalt:
  - 43 krav på bordsservering, 15 krav på förordnade ordningsvakter och 2 krav från räddningstjänsten,
  - 40 villkor saknar kod; om alla har fritext är inte räknat,
  - villkoren har giltighetsdatum (`FR_OM`, `T_OM`) som inte är analyserade.
- **Serveringsställenummer** finns på 128 av 132 serveringsställen och har alltid 8 siffror.
- **Timrå.** 14 av de 132 serveringsställena med gällande tillstånd ligger i området Timrå (analys 2026-10-06). 12 av dem har postorten Söråker, Timrå eller Fagervik. Tobaksdatabasen har också området Timrå (avsnitt 3.4.1). Se beslut 19.
- **Dokumenten ligger inte i databasen.** Tabellerna hänvisar till filer med bara filnamn, utan någon mappsökväg. Enligt AlkT:s inställningar ligger dokumenten inte i databasen och inte i en mapp per serveringsställe (`DocInDB=0`, `Objektmapp=0`). Katalogerna pekas ut i klientens ini-fil (avsnitt 3.5). För de 132 serveringsställena med tillstånd finns 10 234 filreferenser med 10 112 olika filnamn, fördelade på 1 298 ärenden (analys omgång 4):

  |                                    Källa                                     | Referenser |                             Filtyper                              |
  |------------------------------------------------------------------------------|------------|-------------------------------------------------------------------|
  | Dokument som AlkT har skapat vid händelser (`Ärende_Händelser.Worddokument`) | 5 360      | Bara RTF                                                          |
  | Bifogade filer vid händelser (`Ärende_Händelser.Bifogadfil`)                 | 3 534      | PDF 2 609, Outlook-mejl 336, docx 294, doc 150, bilder 86, övrigt |
  | Tillsyn (`Tillsyn_Tillfälle.Bifogadfil`, `Tillsyn_Tillfälle_Filer`)          | 745        | PDF 452, doc 241, övrigt                                          |
  | Beslutsdokument som AlkT har skapat (`Ärende_Beslut.Worddokument`)           | 242        | Bara RTF                                                          |
  | `Ärende_Bilder`                                                              | 216        | Mest doc (191); bara 16 är bilder                                 |
  | Bifogade filer vid beslut (`Ärende_Beslut.BifogadFil`)                       | 98         | PDF 60, docx 21, övrigt                                           |
  | Ritningar på tillstånden (`Gällande_Tillstånd.Ritning`)                      | 26         | Ingen filändelse, troligen beteckningar snarare än filer          |
  | Serveringsställen (`Objekt.ObjBifogadfil`, `ObjBifogadfil2`)                 | 13         | PDF och jpg                                                       |

  Filnamnen är nästan unika, så filerna bör gå att matcha mot filarean på namn. 15 referenser är länkar och 337 är Outlook-mejl.

- **Filer som hör till själva tillstånden** (analys omgång 5). Varje tillstånd kopplas till det ärende som utfärdade det, via beslutet som tillståndet pekar på eller via utfärdandediarienumret. Diarienumren jämförs utan hänsyn till mellanslag, bindestreck och versaler.

  - 113 av 135 tillstånd får en koppling: 111 via diarienumret och 41 via beslutet, delvis samma. 22 saknar koppling, och 18 kopplas till mer än ett ärende. Totalt kopplas 134 ärenden.
  - De kopplade ärendena och serveringsställena har 2 059 filreferenser, med 2 041 olika filnamn:

  |                 Källa                  | Filer |                        Filtyper                         |
  |----------------------------------------|-------|---------------------------------------------------------|
  | Bilagor i ärendets händelser           | 932   | PDF 746, Outlook-mejl 87, docx 47, bilder 32, övrigt 20 |
  | Dokument som AlkT har skapat i ärendet | 980   | RTF                                                     |
  | Beslutsdokument som AlkT har skapat    | 68    | RTF                                                     |
  | Bilagor till besluten                  | 38    | PDF 25, docx 7, övrigt 6                                |
  | Serveringsställenas egna filer         | 13    | PDF 9, bilder 4                                         |
  | Ritning på tillståndet                 | 26    | Ingen filändelse, troligen inga filer                   |
  | Ärendets "bilder"                      | 2     | doc                                                     |

  Bilagorna, som ansökningshandlingar, kartor och ritningar, är alltså cirka 980. Vilka PDF:er som är kartor eller ritningar syns bara i filnamnen, som inte är analyserade. För de 22 tillstånden utan koppling går filerna inte att hitta automatiskt.

### 3.3 Fel och oklarheter i datat

Siffrorna gäller 2026-10-05. Flera beror på dagens datum, så analyserna körs om strax före produktionskörningen.

|                               Avvikelse                                |  Antal   |                                                                                                                                                                Hantering                                                                                                                                                                |
|------------------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tillståndet saknar serveringsställe                                    | 3        | Granskas (G1)                                                                                                                                                                                                                                                                                                                           |
| Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare | 3        | Granskas (G2, beslut 13)                                                                                                                                                                                                                                                                                                                |
| Ägaren saknas i tillståndet, men serveringsstället har en ägare        | 13       | Serveringsställets ägare används (beslut 13)                                                                                                                                                                                                                                                                                            |
| Det kopplade beslutet har passerat sitt sista giltighetsdatum          | 6        | Granskas (G3). Fem gäller tillägg eller en ändring i bolag; där gäller grundtillståndet troligen, men omfattningen kan visa ett tillägg som har upphört. Det sjätte är ett tillfälligt tillstånd för slutet sällskap som gällde till 2026-02-12. Det hör troligen ihop med en period på samma dag, och då har hela tillståndet gått ut. |
| Datumperioden har passerat                                             | 2        | Granskas (G4). Den ena hör troligen till det utgångna tillfälliga tillståndet ovan. Den andra står på ett tillstånd som gäller året runt och är troligen ett utgånget tillägg. Antalet växer: fem av de andra perioderna slutar 2026-10-31 eller 2026-11-30.                                                                            |
| Gäller året runt men har en datumperiod                                | 6        | Granskas (G12). Troligen tillfälligt utökat tillstånd, och omfattningen kan innehålla utökningen.                                                                                                                                                                                                                                       |
| Gäller inte året runt men saknar period                                | 5        | Granskas (G5). Det går inte att se när tillståndet gäller.                                                                                                                                                                                                                                                                              |
| Säsong angiven som dag och månad ("1 maj", "30 sept", "05-01")         | 13       | Tolkas automatiskt. Det som inte går att tolka granskas (G9). 10 av de 13 står på tillstånd som gäller året runt; troligen avser säsongen uteserveringen.                                                                                                                                                                               |
| Serveringsstället saknar serveringsställenummer                        | 4        | Granskas (G6, beslut 9)                                                                                                                                                                                                                                                                                                                 |
| Organisationsnumret finns på flera ägarposter                          | 1 nummer | Granskas (G7)                                                                                                                                                                                                                                                                                                                           |
| Tillståndet pekar på ett beslut som saknas                             | 13       | Påverkar inte migreringen. Noteras i rapporten.                                                                                                                                                                                                                                                                                         |

Rättningar görs som registervård i AlkT före produktionskörningen (beslut 9), så att AlkT är facit vid körningen. Det som inte kan rättas där förs in i en undantagslista (avsnitt 7.1.1). Verksamhetens lista över vad som ska hanteras står i *Att hantera före migrering*.

### 3.4 Tobaksdatabasen (fas 2)

Tobakstillstånden och anmälningarna ligger i databasen `AlkOL2Sundsvall`, på samma SQL Server 2022 som AlkT och med samma sorteringsordning. Kompatibilitetsnivån är 110, alltså högre än AlkT:s. Databasen kommer från samma leverantör som AlkT och är uppbyggd på samma sätt, med ägare, personer med betydande inflytande, ärenden, beslut, händelser och tillsyn. Den är liten: 105 tabeller, varav 50 har rader, och 9 MB. Loggen står för 49 000 av de 57 000 raderna.

Analysen gjordes 2026-10-06 i tre omgångar med samma metod som för AlkT: tabellstruktur, storlek per tabell och sammanräknade siffror. Inga rader med personuppgifter lästes. Kontot har inte behörigheten VIEW DEFINITION, så vyernas kolumner och vilka tabeller de läser syns, men inte deras logik.

Tabellerna som migreringen använder:

|                     Tabell                     |         Rader         |                                                                                                    Innehåll                                                                                                    |                     Används till                      |
|------------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `FörsäljningsStällen`                          | 347, varav 115 aktiva | Försäljningsställe: namn, adress, kategori, område, markeringar för produkttyper, markering för upphört, koppling till ägaren och en kopia av ägarens uppgifter                                                | Försäljningsställe och urval av anmälningar           |
| `FS_GT_Tobak` (vy)                             | 78                    | Systemets lista över gällande tobakstillstånd per försäljningsställe, med typ: detaljhandel (`GTDE`), partihandel (`GTPH`) och distanshandel (`GTDI`). Räknas fram från `Beslut`, `Beslutskoder` och `Ärende`. | Urval av tobakstillstånd                              |
| `Ägare`                                        | 324                   | Tillståndshavare: organisationsnummer, namn, bolagstyp, adress, kontaktuppgifter. Samma kolumner som i AlkT.                                                                                                   | Tillståndshavare                                      |
| `Ärende`                                       | 413                   | Ärenden med typ, diarienummer, datum och sökande                                                                                                                                                               | Ärendet där tillståndet beviljades, anmälningsärenden |
| `Beslut`                                       | 244                   | Beslut med typ, datum och giltighetstid                                                                                                                                                                        | Beviljande beslut och senare beslut om upphörande     |
| `Händelser`                                    | 3 985                 | Händelser per försäljningsställe och ärende, med diarienummer och filnamn                                                                                                                                      | Anmälningar och filer                                 |
| `Sökande`                                      | 129                   | Sökande i ärendena                                                                                                                                                                                             | Avstämning mot ägaren (G19)                           |
| `PBI_ägare`                                    | 231                   | Personer med betydande inflytande                                                                                                                                                                              | Enligt beslut 15                                      |
| `Tillsyn_Tillfälle` och `Tillsyn_Anmärkningar` | 331 och 96            | Tillsynsbesök och anmärkningar                                                                                                                                                                                 | Tillsyn (avsnitt 7.8.6)                               |
| Kodtabeller                                    | –                     | `Ärendetyper`, `Beslutskoder`, `Händelsetyper`, `Kategorier`, `Områden`, `Bolagstyper` och `PBI_Roller`                                                                                                        | Översätter koder till text                            |

Datamodellen i korthet:

```
Ägare 1 ──── n FörsäljningsStällen 1 ──── n Ärende 1 ──── n Beslut
                     │                         │
                     ├──── n Händelser ────────┘
                     └──── 0..1 FS_GT_Tobak (vy, räknas fram från besluten)
```

Det finns ingen tabell för gällande tillstånd som `Gällande_Tillstånd` i AlkT, ingen tabell för villkor och inget serveringsställenummer. Ett tobakstillstånd består av försäljningsstället och det beslut som beviljade tillståndet.

#### 3.4.1 Vad analysen visar

- **Produkttyper.** Bland de 115 aktiva försäljningsställena har 78 folköl, 67 tobak, 62 tobaksfria nikotinprodukter och 52 e-cigaretter. Inget har nikotinläkemedel. 18 har ingen markering alls; 15 av dem har händelser, de flesta från 2026, och är troligen pågående ansökningar eller anmälningar.
- **Tobakstillstånd.** Bara tobak kräver tillstånd. Tillståndet är ett beslut av typen `GTDE`, "Tillstånd tobaksförsäljning – detaljhandel". Det finns inga beviljade tillstånd för partihandel eller distanshandel.
  - Vyn `FS_GT_Tobak` listar 78 försäljningsställen. 67 är aktiva och har markeringen för tobak, och alla aktiva med markeringen finns i vyn. 11 är markerade som upphörda men finns kvar i vyn.
  - Vyn tar inte hänsyn till senare beslut om upphörande på egen begäran (`GTDEU`) eller återkallelse (`GTDEÅ`). 12 av de 78 har ett sådant beslut efter det senaste beviljandet, och minst 3 av dem är aktiva.
  - 65 har ett beviljande beslut, 11 har två och 2 har tre eller fler. Det senaste beviljandet är från 2019–2020 för 53 tillstånd, när tillståndsplikten infördes, och från 2021–2026 för 25.
  - Det senaste beviljande beslutet har giltigt-från-datum på 75 av 78 och inget giltigt-till-datum. Tillstånden gäller alltså tills vidare.
  - 76 av de beviljande ärendena är ansökningar om tobakstillstånd (`WADE`). Ett är registrerat som anmälan om ändring i bolag och ett som anmälan om tobaksfria nikotinprodukter.
  - 26 har fått en godkänd anmälan om ändring i bolag efter beviljandet. Sökanden i det beviljande ärendet är ändå nuvarande ägare i 71 fall, så ändringarna gäller styrelse och ägarandelar, inte vem som har tillståndet. I 2 fall är sökanden en annan än nuvarande ägare, och i 5 saknar sökanden nummer.
  - 29 av de 78 har ett öppet ärende.
- **Diarienummer.** Det beviljande ärendet har diarienummer på 77 av 78. Samma diarienummer används ofta för flera ärenden; 35 diarienummer finns på mer än ett ärende. De flesta är anmälningar om ändring i bolag eller om tobaksfria nikotinprodukter för flera butiker i samma kedja, och tillsyn av flera försäljningsställen. Bland tillstånden finns det beviljande ärendets diarienummer på andra ärenden i 5 fall, och i 1 fall är samma diarienummer beviljande ärende för två försäljningsställen. Formaten är desamma som i AlkT, till exempel "IAN-2019-12345" och "SN 2015-12345".
- **Anmälningar.** Folköl, e-cigaretter och tobaksfria nikotinprodukter anmäls och får inget beslut. En anmälan syns som markeringen på försäljningsstället och ibland som ett anmälningsärende eller en anmälningshändelse:

  |           Produkt           | Aktiva med markering | Med ärende eller händelse | Bara markering | Tidigaste registrering |
  |-----------------------------|----------------------|---------------------------|----------------|------------------------|
  | Folköl                      | 78                   | 34                        | 44             | 2008                   |
  | E-cigaretter                | 52                   | 45                        | 7              | 2009                   |
  | Tobaksfria nikotinprodukter | 62                   | 50                        | 12             | 2019, de flesta 2022   |

  Folköl anmäls antingen för försäljning (`Fföl`, händelse 014) eller för servering (`SÖL`, händelse 015). Markeringen skiljer inte på dem.

- **Tillståndshavare.** 89 ägarposter med 88 olika nummer har aktiva försäljningsställen. Alla nummer har 10 siffror: 79 organisationsnummer och 10 personnummer, som stämmer med de 10 enskilda firmorna. Tillståndshavarna för de 78 tobakstillstånden har 73 organisationsnummer och 5 personnummer. Ägarens uppgifter finns också som kopia på försäljningsstället; organisationsnumret skiljer sig på 1 och namnet på 2.

- **Personer med betydande inflytande.** 172 aktiva poster på ägare med aktiva försäljningsställen, mest ledamöter (96) och suppleanter (31). 3 är utländska personer.

- **Pågående ärenden.** 47 ärenden saknar avslutsdatum, 42 av dem på aktiva försäljningsställen. 28 är anmälningar om ändring i bolag.

- **Tillsyn.** 32 tillsynsärenden och 2 ärenden om inre tillsyn. 331 tillsynsbesök 2005–2026, varav 203 på aktiva försäljningsställen, och 96 anmärkningar.

- **Dokument.** Som i AlkT finns bara filnamn utan sökväg (`DocInDB=0`). Dokument som systemet har skapat har namn med 8 siffror, till exempel `12345678.rtf`. Hela databasen har 2 985 filreferenser. De beviljande ärendena för de 78 tillstånden har 488 filreferenser med 488 olika namn: 269 RTF-dokument som systemet har skapat och 219 bilagor (164 PDF, 16 Outlook-mejl, 13 doc och docx, 13 bilder och 13 övriga). 77 av de 78 beviljande ärendena har minst en fil.

- **Timrå.** Området "Timrå" har 15 aktiva försäljningsställen, med 11 tobakstillstånd och 14 anmälningar om folköl, 6 om e-cigaretter och 11 om tobaksfria nikotinprodukter. AlkT har också området Timrå (avsnitt 3.2). Se beslut 19.

- **Överlapp med AlkT.** 12 av de 88 ägarna har också ett gällande serveringstillstånd i AlkT. 19 försäljningsställen har samma adress som ett serveringsställe med tillstånd, 9 av dem med samma ägare; det är främst restauranger som serverar folköl. Ägarna blir samma part i Party, men ärendena och tillstånden hålls isär.

#### 3.4.2 Fel och oklarheter i datat

Siffrorna gäller 2026-10-06. Analyserna körs om strax före produktionskörningen.

|                                      Avvikelse                                      |                              Antal                               |                                                      Hantering                                                       |
|-------------------------------------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Försäljningsstället är markerat som upphört men finns i vyn över gällande tillstånd | 11                                                               | Räknas som avslutat och migreras inte, om inte undantagslistan säger något annat (G17)                               |
| Beslut om upphörande eller återkallelse efter det senaste beviljandet               | 12, varav minst 3 aktiva                                         | Räknas som avslutat och migreras inte, om inte undantagslistan säger något annat (G18)                               |
| Sökanden i det beviljande ärendet är inte nuvarande ägare                           | 2                                                                | Granskas (G19)                                                                                                       |
| Det beviljande ärendet saknar diarienummer                                          | 1                                                                | Granskas (G15)                                                                                                       |
| Samma diarienummer är beviljande ärende för två försäljningsställen                 | 1 diarienummer                                                   | Granskas (G16)                                                                                                       |
| Ägarens organisationsnummer på försäljningsstället skiljer sig från ägarposten      | 1                                                                | Granskas (G20)                                                                                                       |
| Organisationsnumret finns på flera ägarposter                                       | 1 nummer                                                         | Granskas (G7)                                                                                                        |
| Aktivt försäljningsställe utan ägare, eller med en ägare som saknas                 | 2 och 1, inget av dem har tobakstillstånd                        | Granskas om försäljningsstället har en anmälan (G21)                                                                 |
| Anmälan finns bara som markering, utan ärende eller händelse                        | 63: 44 folköl, 7 e-cigaretter och 12 tobaksfria nikotinprodukter | Upplysning (G22). Anmälan får försäljningsställets tidigaste kända datum. Finns inget datum alls granskas den (G23). |
| Aktivt försäljningsställe utan produkttyp                                           | 18                                                               | Migreras inte. Listas i rapporten.                                                                                   |
| Ärenden som inte är avslutade                                                       | 47, varav 42 på aktiva försäljningsställen                       | Avslutas eller flyttas för hand före produktionskörningen (beslut 23)                                                |

### 3.5 Filarean (fas 2)

Filarean kartlades 2026-10-06 med bara läsåtkomst och sammanräknade uppgifter, utan fil- eller mappnamn. Katalogerna pekas ut i klientprogrammens ini-filer, under nycklar per sorts fil. Sökvägarna står inte här; de anges i appens konfiguration (avsnitt 5.1).

|      Källa      | Nyckel i klientens ini-fil |                              Innehåll                               |                                    Referenser i databasen som pekar dit                                    |
|-----------------|----------------------------|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| AlkT            | `Dokument`                 | Dokument som AlkT har skapat, nästan bara RTF med 8-siffriga namn   | `Ärende_Händelser.Worddokument` och `Ärende_Beslut.Worddokument`                                           |
| AlkT            | `Bilder`                   | Bilagorna, trots namnet: PDF, Word, Outlook-mejl, bilder och länkar | Troligen `Bifogadfil`-kolumnerna, `Ärende_Bilder`, `Tillsyn_Tillfälle_Filer` och serveringsställenas filer |
| Tobaksdatabasen | `Dokument`                 | Både dokument som systemet har skapat och bilagorna                 | `Worddokument` och `Bifogadfil` i `Händelser`, `Beslut` och `Tillsyn_Tillfälle`                            |

Övriga nycklar i ini-filerna, till exempel mallar, etiketter och avgifter, behövs inte för migreringen.

Det här påverkar migreringen:

- **Katalogerna är platta.** I stort sett alla filer ligger direkt i katalogen, utan mappar per serveringsställe, försäljningsställe eller ärende. En fil hittas därför på sitt namn i den katalog som hör till referensen.
- **Namnen på de skapade dokumenten** har samma format som referenserna i databasen, 8 siffror och `.rtf`.
- **Bilagorna har ett prefix i filnamnet** på filarean, till exempel en bokstav, understreck, en bokstav och fem siffror, och ibland en tidsstämpel sist. Prefixet gör troligen namnen unika i den platta katalogen. Om referenserna i databasen har samma prefix är inte kontrollerat; det avgör hur matchningen görs.
- **Kvarlämnade filer** som Word-låsfiler (`~$…`) och `.tmp` finns i katalogerna. Ingen referens pekar på dem, så de berörs inte.
- **Filer större än 50 MB**, gränsen i SupportManagement, finns i alla tre katalogerna. En sådan fil laddas inte upp och noteras i rapporten (G14).
- **Filarean får bara läsas.** Appen skriver, flyttar och raderar aldrig något där (K7).

**Det som återstår** är att matcha referenserna i de ärenden som ska migreras mot katalogerna: hur många av filerna som finns, hur stora de är, hur många som är större än 50 MB och om prefixet behövs. Matchningen görs lokalt och visar bara antal. Därefter är omfånget för beslut 7 känt.

Dokument som inte migreras ska ändå bevaras enligt kommunens dokumenthanteringsplan. Det gäller även ärendehistoriken i AlkT. Hur det görs, till exempel genom leverans till e-arkiv, ligger utanför migreringen men måste vara bestämt innan AlkT stängs.

## 4. Målsystem

|      System       |                                                       Vad det får                                                        |                                        Anrop                                        |
|-------------------|--------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| SupportManagement | Ett avslutat, märkt ärende per tillstånd, anmälan och tillsynsärende i namespace ALKT                                    | Söka på extern tagg, skapa ärende, aktivera ärende. I fas 2 även ladda upp bilagor. |
| PartyAssets       | En post per tillstånd och anmälan, kopplad till ärendet                                                                  | Skapa tillstånd, söka på tillståndets id tillsammans med partyId                    |
| Party             | Ingenting. Används för att slå upp partyId.                                                                              | Slå upp partyId från organisations- eller personnummer                              |
| licensed-business | Ingenting. Serveringsställenumret och dess nuvarande tilldelning stäms av mot registret (avsnitt 7.6). Gäller bara AlkT. | Slå upp ett serveringsställenummer och dess tilldelning                             |
| pw-alkt           | Ingenting. Får aldrig nås av migreringen.                                                                                | Inga                                                                                |

## 5. Lösningsöversikt

```
AlkT (SQL Server) ─┐                                ┌─► Party              (partyId)
                   ├─► Migreringsapp ──────────────┼─► licensed-business  (serveringsställenummer)
Tobaksdatabas ─────┤   · källor per databas         ├─► PartyAssets        (tillstånd, länkat till ärendet)
(fas 2)            │   · regler och mappning        └─► SupportManagement  (ärende: utkast → aktivt)
Filarea (fas 2) ───┘   · statustabell och rapport
                                                        ✕ pw-alkt    ingen process får starta
                                                        ✕ Messaging  inga mail eller sms
```

### 5.1 Migreringsappen

- **Teknik:** tjänsten `api-service-aot-loader`, byggd på Spring Boot med dept44 8 (Java 25). Statustabell, undantagslista, uppslag av organisationsnummer och dubblettskydd byggs i tjänsten.
- **Klienter:** klienterna mot SupportManagement, PartyAssets och licensed-business genereras från samma API-specifikationer som pw-alkt använder, så fältnamn och versioner stämmer. Party-klienten är ny: pw-alkt slår bara upp person- eller organisationsnummer från partyId, inte tvärtom.
- **Källor:** en läsare per källdatabas, först AlkT och sedan tobaksdatabasen. Varje läsare gör om sina rader till en gemensam intern modell av ett tillstånd. Därefter är resten av flödet gemensamt. Tobaksläsaren läser vyn `FS_GT_Tobak` för tobakstillstånden och markeringarna på försäljningsställena för anmälningarna (7.8.1).
- **Egen databas:** statustabell och undantagslista (avsnitt 7.1.1 och 8). Den innehåller inga namn eller nummer på personer eller bolag.
- **Körlägen:**
  - provkörning, som bara läser och skriver en rapport,
  - skarp körning, med en gräns för hur många tillstånd som körs åt gången,
  - omkörning, som fortsätter där varje tillstånd stannade.
- **En körning åt gången.** Appen startas manuellt och vägrar starta om en annan körning pågår. Det är en förutsättning för dubblettskyddet (avsnitt 6).
- **Var den körs:** på en utvecklardator (beslut 10), med VPN-åtkomst till AlkT:s SQL Server och API-gatewayen. Statustabellen och undantagslistan ligger i en lokal databas på datorn och sparas efter körningen som underlag. Säkerhetskraven står i avsnitt 12.
- **Konfiguration:** katalogerna på filarean anges i `application.yml` med nycklarna `aot-loader.file-area.alkt.documents`, `.alkt.attachments`, `.tobak.documents` och `.tobak.attachments`. Värdena kommer från miljövariablerna `ALKT_DOCUMENTS_DIR`, `ALKT_ATTACHMENTS_DIR`, `TOBAK_DOCUMENTS_DIR` och `TOBAK_ATTACHMENTS_DIR`. På utvecklardatorn ligger de i en lokal `.env` som git ignorerar. För tobaksdatabasen pekar båda nycklarna på samma katalog. Appen kontrollerar vid start att katalogerna finns och går att läsa, annars startar den inte, och den öppnar filerna bara för läsning.

## 6. Flöde per tillstånd

Varje tillstånd går igenom samma steg. Varje steg kan köras om, och appen fortsätter från det steg som senast lyckades.

|        Steg         |                                                                                                                                     Vad som händer                                                                                                                                     |                                Skydd mot dubbletter                                 |                         Om det går fel                         |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------------|
| 0. Läs och bedöm    | Källraden läses och prövas mot granskningsreglerna (avsnitt 7.1.1) och undantagslistan.                                                                                                                                                                                                | –                                                                                   | Tillstånd som ska granskas stannar här och listas i rapporten. |
| 1. Tillståndshavare | Organisations- eller personnumret görs om till partyId via Party.                                                                                                                                                                                                                      | Uppslag, ingen skrivning                                                            | Saknas i Party: tillståndet granskas (G10).                    |
| 2. Ärende           | Appen söker efter ett ärende med den externa taggen `diarienummer`, med tillståndets diarienummer i AlkT som värde. Finns inget skapas ett ärende som utkast, med taggen.                                                                                                              | Diarienumret i den externa taggen (beslut 4, se nedan)                              | Felet sparas och steget görs om vid nästa körning.             |
| 3. Bilagor (fas 2)  | Filerna från det ärende som utfärdade tillståndet och serveringsställets egna filer (beslut 7) hämtas från filarean och laddas upp till utkastet. Länkar (`.url`) hoppas över. En fil som saknas på filarean, eller är större än 50 MB, laddas inte upp och noteras i rapporten (G14). | Varje fil laddas upp en gång per ärende; filnamn och innehåll jämförs vid omkörning | Som ovan                                                       |
| 4. Tillstånd        | Tillståndet skapas i PartyAssets med ärendets id som `assetId`, diarienumret som parameter och en länk till ärendet. Svar 409 betyder att det redan finns, och då hämtas det med tillståndets id och partyId.                                                                          | Tillståndets id (`assetId`)                                                         | Som ovan                                                       |
| 5. Aktivera         | Ärendet aktiveras. Först nu syns det för handläggarna.                                                                                                                                                                                                                                 | Ett ärende som redan är aktivt hoppas över                                          | Som ovan                                                       |
| 6. Klart            | Statustabellen får ärende-id, ärendenummer, tillståndets id (`assetId`) och PartyAssets interna id.                                                                                                                                                                                    | –                                                                                   | –                                                              |

**Den externa taggen.** En extern tagg är ett par av nyckel och värde på ärendet (fältet `externalTags`). Den kopplar ärendet till en post i ett annat system, och handläggarna ser den normalt inte. Det är inte samma sak som `externalReferences`, som bara finns på konversationer. sm-loader använder samma mekanism för att koppla ärenden till Open-E.

**Diarienumret som nyckel (beslut 4).** Diarienumret i AlkT är nyckeln mot det gamla systemet, både på ärendet och på tillståndet. Det kräver att varje tillstånd har ett diarienummer och att inget diarienummer finns på två tillstånd. 9 tillstånd saknar diarienummer i dag; de och eventuella dubbletter granskas (G15, G16) och rättas genom registervård i AlkT.

**Dubblettskyddet** bygger på tre saker:
- Sökningen i steg 2 måste ange `lifecycle` (DRAFT eller ACTIVE). Annars kommer utkast inte med, och en omkörning efter ett avbrott skulle skapa ett andra utkast.
- Varken SupportManagement eller PartyAssets har en databasregel som hindrar två ärenden med samma tagg eller två tillstånd med samma id. Kontrollen görs i kod, så skyddet förutsätter att bara en körning pågår åt gången.
- Tillståndets `assetId` är ärendets id. Hittar omkörningen ärendet via diarienumret får tillståndet samma id igen, och PartyAssets svarar 409 i stället för att skapa en dubblett.
- PartyAssets svar 409 innehåller inget id. Tillståndet hämtas därför med en sökning på tillståndets id och partyId.

**Ordningen.** Ett utkast syns inte för handläggarna och utlöser varken åtgärder, notiser eller processer. Därför skapas ärendet som utkast och aktiveras sist, när allt annat är klart. Tillståndet i PartyAssets är däremot aktivt från steg 4. Stoppas körningen mellan steg 4 och 5 finns alltså ett aktivt tillstånd kopplat till ett ärende som ännu inte syns. Omkörningen slutför det.

**Tobak.** Tobakstillstånden och anmälningarna går igenom samma steg. För anmälningarna (7.8.5) söker steg 2 på den externa taggen `tobakAnmalan` i stället för `diarienummer` (beslut 22).

**Samma diarium i båda källorna.** AlkT och tobaksdatabasen använder samma diarium, så ett tobakstillstånd kan ha samma diarienummer som ett serveringstillstånd. Sökningen i steg 2 kontrollerar därför också att det ärende som hittas har samma källa och käll-id (parametern `alktObjektId` eller `tobakFsId`). Hittas ett ärende från den andra källan stannar tillståndet för granskning (G16).

## 7. Mappning

### 7.1 Urval och bedömning

|       Bedömning       |                                  Villkor                                  |                                  Resultat                                  |
|-----------------------|---------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Migreras              | Ingen granskningsregel slår till, eller undantagslistan säger "migrera"   | Aktivt ärende och tillstånd med status ACTIVE                              |
| Migreras som utgånget | Undantagslistan säger "migrera som utgånget"                              | Aktivt ärende och tillstånd med status EXPIRED                             |
| Migreras inte         | G17 eller G18 slår till och undantagslistan säger inte "migrera"          | Inget skapas. Tillståndet listas i rapporten.                              |
| Granskas              | Någon granskningsregel slår till och tillståndet saknas i undantagslistan | Inget skapas förrän det är rättat i källan eller avgjort i undantagslistan |

Utgångna tillstånd hanteras alltså alltid via granskningen: verksamheten avgör per tillstånd om det avslutas i AlkT, migreras som utgånget eller migreras ändå (beslut 1 och 14).

I tobaksdatabasen ligger avslutade tillstånd kvar i vyn över gällande tillstånd (7.8.1), och vi vet inte hur de tas bort därifrån. Ett tillstånd som träffas av G17 eller G18 räknas därför som avslutat och migreras inte, om inte verksamheten för in det i undantagslistan med utfallet "migrera".

#### 7.1.1 Granskning

Vilka tillstånd som ska granskas avgörs av regler, inte av handpåläggning. Samma regler används varje gång, så listan blir densamma tills datat ändras.

| Nr  |                                                                                     Regel                                                                                     |     Kontrolleras mot     |                                    Antal 2026-10-05                                    |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|----------------------------------------------------------------------------------------|
| G1  | Tillståndet saknar serveringsställe                                                                                                                                           | AlkT                     | 3                                                                                      |
| G2  | Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare                                                                                                        | AlkT                     | 3                                                                                      |
| G3  | Det kopplade beslutet har passerat sitt sista giltighetsdatum                                                                                                                 | AlkT                     | 6                                                                                      |
| G4  | Datumperioden har passerat                                                                                                                                                    | AlkT                     | 2                                                                                      |
| G5  | Gäller inte året runt men saknar period                                                                                                                                       | AlkT                     | 5                                                                                      |
| G6  | Serveringsstället saknar serveringsställenummer                                                                                                                               | AlkT                     | 4                                                                                      |
| G7  | Ägarens organisationsnummer finns på flera ägarposter                                                                                                                         | AlkT                     | 1 nummer                                                                               |
| G8  | Ägarens nummer saknas eller har fel format                                                                                                                                    | AlkT                     | 0                                                                                      |
| G9  | En säsong, period, tid, ett datum eller ett villkors giltighetsdatum går inte att tolka                                                                                       | AlkT                     | Okänt                                                                                  |
| G10 | Tillståndshavaren finns inte i Party                                                                                                                                          | Party                    | Syns först vid provkörning                                                             |
| G11 | Serveringsställenumret finns inte i registret i licensed-business, eller registrets nuvarande tilldelning gäller ett annat organisationsnummer än tillståndshavarens          | licensed-business        | Syns först när registret är inläst                                                     |
| G12 | Gäller året runt men har en datumperiod                                                                                                                                       | AlkT                     | 6                                                                                      |
| G13 | Tillståndet går inte att koppla till ärendet som utfärdade det. Upplysning, stoppar inte migreringen: tillståndet får inga filer automatiskt.                                 | AlkT                     | 22                                                                                     |
| G14 | En fil som ska följa med finns inte på filarean, eller är större än 50 MB. Upplysning, stoppar inte migreringen.                                                              | Filarean                 | Syns vid matchningen mot filarean (3.5)                                                |
| G15 | Tillståndet saknar diarienummer (beslut 4)                                                                                                                                    | AlkT och tobaksdatabasen | 9 i AlkT och 1 i tobaksdatabasen                                                       |
| G16 | Diarienumret finns på mer än ett tillstånd (beslut 4)                                                                                                                         | AlkT och tobaksdatabasen | AlkT okänt, syns i granskningslistan. I tobaksdatabasen 1 diarienummer på 2 tillstånd. |
| G17 | Försäljningsstället är markerat som upphört men har gällande tobakstillstånd enligt vyn. Tillståndet räknas som avslutat och migreras inte (7.1).                             | Tobaksdatabasen          | 11                                                                                     |
| G18 | Ett beslut om upphörande eller återkallelse är senare än det senaste beviljande beslutet. Tillståndet räknas som avslutat och migreras inte (7.1).                            | Tobaksdatabasen          | 12, varav minst 3 på aktiva försäljningsställen                                        |
| G19 | Sökanden i det beviljande ärendet är inte nuvarande ägare                                                                                                                     | Tobaksdatabasen          | 2                                                                                      |
| G20 | Ägarens organisationsnummer på försäljningsstället skiljer sig från ägarposten                                                                                                | Tobaksdatabasen          | 1                                                                                      |
| G21 | Försäljningsstället saknar ägare, eller ägaren saknas                                                                                                                         | Tobaksdatabasen          | 0 bland tillstånden, högst 3 bland anmälningarna                                       |
| G22 | Anmälan finns bara som markering på försäljningsstället. Upplysning, stoppar inte migreringen: anmälan får försäljningsställets tidigaste kända datum och inget diarienummer. | Tobaksdatabasen          | 63                                                                                     |
| G23 | Anmälan saknar datum, och försäljningsstället har inga händelser eller ärenden. Posten i PartyAssets kräver ett datum.                                                        | Tobaksdatabasen          | Okänt, syns i granskningslistan                                                        |

Ett tillstånd kan träffas av flera regler. I dag ger G1–G8, G12 och G15 drygt 30 träffar i AlkT. I tobaksdatabasen ger G15, G16 och G19–G21 högst 6 träffar bland tillstånden, och G17 och G18 gör att högst 23 tillstånd inte migreras. G7, G8, G10 och G14 gäller båda källorna, G1–G6, G9 och G11–G13 bara AlkT och G17–G23 bara tobaksdatabasen.

**Hur listan tas fram.**
- Provkörningen tillämpar alla regler och listar varje tillstånd som träffas, med regel och detalj (avsnitt 8).
- Reglerna som bara bygger på AlkT kan prövas innan appen finns, med en fråga mot AlkT som verksamheten kör själv. Svaret innehåller namn och nummer och stannar hos verksamheten.
- G10 och G11 kräver uppslag i Party och licensed-business och syns därför först vid provkörningen.

**Vem gör vad.** Verksamheten går igenom listan och avgör varje tillstånd. Utfallet är något av följande:

|                  Utfall                  |                                                          Hur det genomförs                                                          |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Rätta uppgiften                          | Rättas genom registervård i AlkT före produktionskörningen (beslut 9). Nästa provkörning visar att tillståndet inte längre träffas. |
| Avsluta tillståndet                      | Avslutas i AlkT, så att det inte längre är gällande. Det migreras då inte.                                                          |
| Migrera ändå, eller migrera som utgånget | Förs in i undantagslistan med käll-id, utfall, vem som beslutade och när. Används bara när rättningen inte kan göras i AlkT.        |

**När listan ska vara tom.** Provkörningen i produktion, direkt före den skarpa körningen, får bara visa tillstånd som finns i undantagslistan. Annars stoppas den skarpa körningen.

Tobaksdatabasen hanteras på samma sätt. Rättningar görs där, och verksamheten kör granskningsfrågan `granskningslista_tobak.sql` som hör till listan *Att hantera före migrering* för tobak.

### 7.2 Tillståndshavare

Tillståndshavaren är serveringsställets nuvarande ägare (`Objekt.AgarID` → `Ägare`). Ägaren i tillståndet (`GTAgarID`) används bara för att hitta avvikelser (G2).

|                Källa                 |                          Mål                          |                                                                                                                                                                                                                                  Regel                                                                                                                                                                                                                                   |
|--------------------------------------|-------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Ägare.OrganisationsNr`              | Uppslag i Party                                       | Bindestreck, plustecken och mellanslag tas bort. Tio siffror med tredje siffran 2 eller högre är ett organisationsnummer och slås upp som ENTERPRISE med tio siffror. Annars är det ett personnummer (enskild firma) som slås upp som PRIVATE med tolv siffror. Århundradet väljs så att födelsedatumet ligger före dagens datum och åldern är under 100 år, eller minst 100 år om numret skrevs med plustecken. Formatet för PRIVATE verifieras mot Party i testmiljön. |
| partyId från Party                   | Intressentens `externalId` och tillståndets `partyId` | –                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| Typ av nummer                        | Intressentens `externalIdType`                        | ENTERPRISE eller PRIVATE                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| –                                    | Intressentens `role`                                  | `PRIMARY`, samma roll som pw-alkt läser som tillståndshavare                                                                                                                                                                                                                                                                                                                                                                                                             |
| `Ägare.Bolagsnamn`                   | `organizationName`                                    | Även för enskild firma, eftersom fältet innehåller firmanamnet                                                                                                                                                                                                                                                                                                                                                                                                           |
| `GAdress`, `GAdress2`, `PNr`, `POrt` | `address`, `zipCode`, `city`                          | `GAdress2` läggs efter `GAdress`                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `EMail`, `TelefonNr1`                | Kontaktkanaler EMAIL och PHONE                        | Tomma värden utelämnas                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `KontaktPerson`                      | Parameter `kontaktperson`                             | Förslag. Alternativet är en egen intressent med kontaktroll, om namespace har en sådan roll.                                                                                                                                                                                                                                                                                                                                                                             |

**Personer med betydande inflytande (om beslut 15 säger ja).** Varje aktiv post i `PBI_ägare` för tillståndshavaren blir en intressent i ärendet:
- Rollen tas från befattningskoden via kodlista P (VD, firmatecknare, ledamot och så vidare) och måste finnas som roll i ALKT.
- `externalId` hämtas från Party som PRIVATE. Personer utan svenskt personnummer (`UtlandskPerson`) får ingen `externalId` utan bara namn.
- Samma person kan stå på flera ärenden om ägaren har flera tillstånd.

#### 7.2.1 Serveringsstället som intressent

Serveringsstället blir en egen intressent i ärendet, och serveringsställenumret lagras som parameter på den intressenten (beslut 18). pw-alkt kommer att göra likadant för nya tillstånd. Rollens namn och parameterns nyckel bestäms tillsammans med dem som äger pw-alkt, så att nya och migrerade ärenden blir likadana.

|                    Källa (`Objekt`)                    |                        Mål på intressenten                         |                                                                            Regel                                                                            |
|--------------------------------------------------------|--------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| –                                                      | `role`                                                             | Rollen för serveringsställe, samma som pw-alkt använder. Rollen måste finnas i ALKT.                                                                        |
| –                                                      | `externalId`, `externalIdType`                                     | Lämnas tomma. Ett serveringsställe är ingen part i Party.                                                                                                   |
| `ServeringsNamn`                                       | `organizationName`                                                 | –                                                                                                                                                           |
| `ServeringsGAdress`, `ServeringsPNr`, `ServeringsPOrt` | `address`, `zipCode`, `city`                                       | –                                                                                                                                                           |
| `TelefonNr1`, `Email`                                  | Kontaktkanaler PHONE och EMAIL                                     | Tomma värden utelämnas                                                                                                                                      |
| `RestaurangBeteckning`                                 | Parameter `restaurantNumber` (förslag, nyckeln som pw-alkt väljer) | 8 siffror. Saknas numret granskas tillståndet (G6).                                                                                                         |
| `ObjektGrupp1` med kodlista Q                          | Parameter `typAvServeringsstalle` (förslag)                        | Text, till exempel "Kvartersrestaurang". 13 serveringsställen saknar typ. Koderna UNG och UTOK är markeringar, inte typer, och blir parametern `markering`. |

Intressentens parametrar följer samma regler som ärendets: nyckeln högst 255 tecken och varje värde högst 3 000 tecken.

### 7.3 Ärende i SupportManagement

|       Fält        |                                                                                                 Värde                                                                                                  |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `title`           | "Serveringstillstånd – {serveringsställets namn}"                                                                                                                                                      |
| `description`     | En läsbar sammanfattning av drycker, vem serveringen gäller, omfattning, tider och villkor                                                                                                             |
| `priority`        | MEDIUM                                                                                                                                                                                                 |
| `status`          | En avslutad status i ALKT (beslut 5)                                                                                                                                                                   |
| `classification`  | Kategori och typ för befintliga tillstånd (beslut 5)                                                                                                                                                   |
| `labels`          | Samma etiketter som ett vanligt ärende av samma typ, även etiketter med `processKey`, plus etiketten "Migrerat ärende". Etiketten spärrar alla processer för ärendet (avsnitt 7.3.1, beslut 5 och 17). |
| `channel`         | MIGRATION (förslag)                                                                                                                                                                                    |
| `reporterUserId`  | alkt-migration. Fältet är obligatoriskt och ingen person har anmält ärendet.                                                                                                                           |
| `assignedUserId`  | Tomt, så att ingen handläggare får en notis om ärendet. Prenumeranter får ändå interna notiser när ärendet aktiveras, se R3.                                                                           |
| `businessRelated` | true                                                                                                                                                                                                   |
| `lifecycle`       | DRAFT när ärendet skapas, ACTIVE i sista steget                                                                                                                                                        |
| `externalTags`    | `diarienummer` = tillståndets diarienummer i AlkT, nyckeln mot AlkT och skyddet mot dubbletter (beslut 4, avsnitt 6)                                                                                   |
| `stakeholders`    | Tillståndshavaren, serveringsstället med serveringsställenumret som parameter (avsnitt 7.2.1) och, om beslut 15 säger det, personer med betydande inflytande (avsnitt 7.2)                             |
| `parameters`      | Enligt tabellen nedan                                                                                                                                                                                  |

#### 7.3.1 Märkning av migrerade ärenden

Migrerade ärenden märks på fyra sätt. Etiketten är det handläggarna ser. De övriga tre är tekniska och kommer med automatiskt.

|                     Märkning                     |     Syns för handläggare     | Går att filtrera på |                                      Syfte                                       |
|--------------------------------------------------|------------------------------|---------------------|----------------------------------------------------------------------------------|
| Etiketten "Migrerat ärende"                      | Ja, bland ärendets etiketter | Ja                  | Visar att ärendet är migrerat och att uppgifterna kommer från det gamla systemet |
| Parametern `migrerad` ("ÅÅÅÅ-MM-DD från AlkT")   | Ja, bland parametrarna       | Ja                  | Visar när och varifrån ärendet migrerades                                        |
| Kanal `MIGRATION`                                | Beror på gränssnittet        | Ja                  | Skiljer migrerade ärenden från inkomna                                           |
| Extern tagg `diarienummer` (diarienumret i AlkT) | Normalt inte                 | Ja                  | Nyckel mot källsystemet och skydd mot dubbletter                                 |

Etiketten läggs som en egen etikett i ALKT, utanför trädet med tillståndstyper. Källsystemet (AlkT eller tobaksdatabasen) syns i parametern och den externa taggen, så det räcker med en etikett för båda källorna.

#### 7.3.2 Spärr mot processer

Migrerade ärenden får samma etiketter som vanliga ärenden, så att de går att söka fram och sortera på samma sätt. Det betyder att de bär etiketter med `processKey`.

**Utan spärr startar en process första gången en handläggare ändrar ett migrerat ärende.**
- Headern `X-Trigger-Process: false` skyddar bara migreringens egna anrop och gäller inte för AD-konton (R1).
- Processen startar automatiskt eftersom etiketten pekar ut en process och ärendet aldrig har haft någon (`ProcessEventPublisher.java` rad 337–348).
- Startkommandot (knappen "Starta process") och signaler passerar dessutom både headern och namespacets triggers (`ProcessEventPublisher.java` rad 226, `EventSubType.java` rad 27–28).
- SupportManagement har i dag inget sätt att stänga av processer för ett enskilt ärende. En etikett utan `processKey` räknas inte i processvalet, oavsett vilka andra attribut den har (`ProcessKeySelector.java` rad 161–165).

**Spärretiketten (beslut 17).** Spärren görs i SupportManagement och måste vara driftsatt innan ärendena migreras i produktion:
- Etiketten "Migrerat ärende" får attributet `processBlocked=true`.
- För ett ärende med en sådan etikett skrivs ingen processhändelse alls, oavsett händelsetyp, övriga etiketter och status. Det gäller även startkommandot och signaler; startkommandot svarar med ett fel som förklarar spärren.
- En etikett med attributet kan inte tas bort av ett AD-konto.
- Ändringen görs på två ställen: i processpubliceringen och i kontrollen när etiketter ändras.

Inga processer kan alltså startas på migrerade ärenden, och pw-alkt nås aldrig. Ny handläggning av ett migrerat tillstånd, till exempel en ändringsansökan, görs i ett nytt ärende. Om etiketten ändå måste tas bort görs det av en administratör med en tjänsteidentitet, och ärendet behandlas då som ett vanligt ärende.

#### 7.3.3 Parametrar

Skapat-datum och ärendenummer sätts alltid av SupportManagement. Ärendet får därför migreringsdagen som skapat-datum och ett ärendenummer från migreringsmånaden. De ursprungliga uppgifterna ligger i parametrarna (beslut 6).

Tomma värden utelämnas, och ett värde får vara högst 3 000 tecken. Serveringsställets namn, adress, nummer och typ ligger på intressenten för serveringsstället (avsnitt 7.2.1), inte bland ärendets parametrar.

| Nyckel (förslag)  |     Visningsnamn      |                                                                   Källa                                                                   |                                                                           Format                                                                           |
|-------------------|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `alktObjektId`    | AlkT-id               | `ObjektID`                                                                                                                                | Tal                                                                                                                                                        |
| `utfardat`        | Utfärdat              | `Utfardande_Datum`                                                                                                                        | ÅÅÅÅ-MM-DD                                                                                                                                                 |
| `diarienummer`    | Diarienummer i AlkT   | `Utfardande_Diarienr`                                                                                                                     | Som det står. Formaten varierar, till exempel "IAN-2019-12345" och "SN 2015-12345". Samma värde ligger i den externa taggen och på tillståndet (beslut 4). |
| `ersatter`        | Ersätter              | `Ersatter_Datum`, `Ersatter_Diarienr`                                                                                                     | "ÅÅÅÅ-MM-DD, diarienummer"                                                                                                                                 |
| `drycker`         | Drycker               | `Starkol`, `Vin`, `Spritdrycker`, `AJADrycker`, `ALP`                                                                                     | Ett värde per dryck, till exempel "Starköl", "Andra jästa alkoholdrycker". Vad `ALP` betyder bekräftas av verksamheten.                                    |
| `serveringTill`   | Servering till        | `ServeringAllmanhet`, `ServeringSlutetSallskap`                                                                                           | "Allmänheten", "Slutna sällskap"                                                                                                                           |
| `omfattning`      | Omfattning            | `Uteservering`, `Catering`, `Provsmakning`, `Gardsforsaljning`, `Kryddning`, `Roomservice`, `Minibar`, `Pausservering`, `Trafikservering` | Ett värde per markering                                                                                                                                    |
| `serveringstider` | Serveringstider       | `ServeringStartTid1`–`8`, `ServeringSlutTid1`–`8`                                                                                         | Ett värde per ifyllt intervall, "11:00–01:00"                                                                                                              |
| `aretRunt`        | Året runt             | `AretRunt`                                                                                                                                | Ja eller Nej                                                                                                                                               |
| `sasong`          | Säsong                | `ArligPeriodStart`/`Slut`, eller `PeriodStart`/`Slut` när de innehåller dag och månad                                                     | "MM-DD–MM-DD"                                                                                                                                              |
| `period`          | Period                | `PeriodStart`/`PeriodSlut` när de innehåller datum                                                                                        | "ÅÅÅÅ-MM-DD–ÅÅÅÅ-MM-DD"                                                                                                                                    |
| `hogstaAntal`     | Högsta antal gäster   | `Hogstaantal`                                                                                                                             | Som det står                                                                                                                                               |
| `sittplatser`     | Sittplatser           | `Sittplatser2`                                                                                                                            | Som det står                                                                                                                                               |
| `serveringslokal` | Serveringslokal       | `Serveringslokal`                                                                                                                         | Text                                                                                                                                                       |
| `villkor`         | Villkor               | `ObjVillkor` med kodlista O                                                                                                               | Ett värde per villkor, "Krav på bordsservering: fritext". Villkor vars slutdatum (`T_OM`) har passerat tas inte med.                                       |
| `noteringar`      | Noteringar i AlkT     | `Noteringar` till `Noteringar8`                                                                                                           | Ett värde per ifyllt fält                                                                                                                                  |
| `senasteBeslut`   | Senaste beslut i AlkT | Kopplat beslut, om det finns                                                                                                              | "Beslutstyp, beslutsdatum, giltigt till"                                                                                                                   |
| `migrerad`        | Migrerad              | –                                                                                                                                         | "ÅÅÅÅ-MM-DD från AlkT"                                                                                                                                     |

Nycklarna är förslag och samordnas med parametrarna i pw-alkt (beslut 3).

### 7.4 Tillstånd i PartyAssets

|          Fält          |                                                                                                                 Värde                                                                                                                  |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `assetId`              | Ärendets id, ett UUID som för nya tillstånd (beslut 4). Det gör omkörningar säkra mot dubbletter (avsnitt 6).                                                                                                                          |
| `origin`               | SUPPORTMANAGEMENT, samma som för nya tillstånd (beslut 4). Migrerade tillstånd känns igen på parametrarna `diarienummer` och `migreradFran`.                                                                                           |
| `partyId`              | Från Party, enligt 7.2                                                                                                                                                                                                                 |
| `type`                 | Samma typ som AoT-lösningen ger serveringstillstånd. Det blir inte PERMIT, som pw-alkt använder i dag; typen är inte bestämd (beslut 3 och 20).                                                                                        |
| `title`                | Samma namn som tillstånd som skapas i AoT-lösningen (beslut 4). pw-alkt tar namnet från beslutets rubrik, till exempel "Beslut om serveringstillstånd"; namnet per tillståndstyp hämtas därifrån.                                      |
| `description`          | "Serveringstillstånd för {serveringsställets namn}"                                                                                                                                                                                    |
| `issued`               | `Utfardande_Datum`, finns på alla 135 tillstånd                                                                                                                                                                                        |
| `validTo`              | Sätts bara när tillståndet inte gäller året runt och har en datumperiod; då blir periodens slut slutdatum. I dag gäller det ett tillstånd som inte är granskat. Övriga datumperioder fångas av G4 och G12.                             |
| `status`               | ACTIVE, eller EXPIRED när undantagslistan säger "migrera som utgånget"                                                                                                                                                                 |
| `statusReason`         | Utelämnas. PartyAssets har i dag inga skäl konfigurerade för ACTIVE eller EXPIRED, och en tom sträng avvisas. Det kontrolleras före körningen.                                                                                         |
| `additionalParameters` | Se nedan                                                                                                                                                                                                                               |
| Länk till ärendet      | Query-parametern `sourceReference` på `POST /2281/assets`, URL-kodad: `LINK\|{errandId};case;supportmanagement;ALKT\|`. Samma format som pw-alkt skickar till `/asset-drafts`. PartyAssets skriver då en relation i relationstjänsten. |
| Header                 | `X-Sent-By: alkt-migration; type=migration`, så att migreringen syns som aktör i tillståndets historik                                                                                                                                 |

**`additionalParameters`** får högst vara 255 tecken per värde. Databaskolumnen är `varchar(255)` och API:et kontrollerar inte längden, så ett för långt värde ger ett fel vid sparandet. Därför hålls tillståndets parametrar korta, och längre text finns bara i ärendets parametrar:

|       Nyckel       |                                                                                  Värde                                                                                   |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `errandId`         | Ärendets id, som pw-alkt                                                                                                                                                 |
| `conditions`       | Villkoren, sammanslagna med radbrytning, under samma nyckel som pw-alkt använder. Är texten längre än 255 tecken kortas den och avslutas med en hänvisning till ärendet. |
| `restaurantNumber` | Serveringsställenumret, 8 siffror. Samma nyckel som pw-alkt (beslut 18).                                                                                                 |
| `serveringsstalle` | Serveringsställets namn                                                                                                                                                  |
| `drycker`          | Kommaseparerad lista                                                                                                                                                     |
| `serveringTill`    | Kommaseparerad lista                                                                                                                                                     |
| `diarienummer`     | Diarienumret i AlkT, som det står (beslut 4)                                                                                                                             |
| `migreradFran`     | AlkT                                                                                                                                                                     |

pw-alkt skriver i dag `errandId`, `legalBasis`, `delegationReference`, beslutets egna parametrar och `conditions`. Vilka av de migrerade parametrarna som ska ha samma nycklar som pw-alkt avgörs i beslut 3.

Samma längdgräns gäller pw-alkt. Där kan `conditions` bli längre än 255 tecken om ett beslut har många villkor, och då misslyckas tillståndet. Det bör tas upp separat med dem som äger pw-alkt.

**Dokument i fas 2.** Tillståndet skapas direkt som ACTIVE. Om dokument läggs till senare blir varje uppladdning en ny version av samma tillstånd. Tillstånd med status EXPIRED tar inte emot bilagor alls, och det gäller även tillstånd som nattjobbet har satt till EXPIRED. Ska dokumenten följa med från början bör produktionskörningen därför vänta på fas 2 (beslut 12).

### 7.5 Tolkningsregler

|             Uppgift             |                                                                                                     Regel                                                                                                     |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Datum                           | Datumdelen tas som den står. SQL Servers `datetime` saknar tidszon, så ingen omräkning görs.                                                                                                                  |
| Serveringstider                 | `hh:mm` för start och slut. En sluttid före starttiden betyder efter midnatt. Tomma intervall hoppas över.                                                                                                    |
| Säsong                          | Start och slut ligger i var sitt fält. Dag och svensk månad i hel eller förkortad form ("1 maj", "01 maj", "30 sep", "30 sept", "31 okt", "1 juni"), eller "MM-DD". Det som inte går att tolka granskas (G9). |
| Datumperiod                     | ÅÅÅÅ-MM-DD                                                                                                                                                                                                    |
| Villkorens giltighetsdatum      | Formatet är inte analyserat än. Det som inte går att tolka granskas (G9).                                                                                                                                     |
| Organisations- och personnummer | Enligt 7.2                                                                                                                                                                                                    |
| Markeringar (ja/nej)            | Blir värden i en lista, till exempel `drycker`                                                                                                                                                                |
| Text                            | Mellanslag i början och slutet tas bort. Tomma värden utelämnas.                                                                                                                                              |

### 7.6 Serveringsställenummer

**Läget i dag.**
- licensed-business är registret över serveringsställenummer. Ett nummer tilldelas en tillståndshavare (organisationsnummer och namn), ett serveringsställe och en adress under en period. Registret har ingen koppling till ärenden i SupportManagement eller tillstånd i PartyAssets.
- Registret fylls en gång från Excel-registret, konverterat till CSV, via licensed-business importtjänst.
- pw-alkt har kod som tilldelar ett nummer i beslutsfasen: den slår upp eller skapar adressen, tar ett ledigt nummer eller skapar ett nytt, och skapar tilldelningen (`LicensedBusinessIntegration.assignRestaurantNumber`). Koden anropas inte någonstans på `origin/main`, så nya tillstånd får i dag inget nummer lagrat i SupportManagement eller PartyAssets.
- SupportManagement har inget eget fält för serveringsställenummer.

**Vad migreringen gör.**

|        Var        |              Vad              |                                                                                                              Hur                                                                                                              |
|-------------------|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AlkT              | Läser numret                  | `Objekt.RestaurangBeteckning` (8 siffror, saknas på 4 serveringsställen)                                                                                                                                                      |
| licensed-business | Stämmer av, skriver ingenting | Hämtar numret och dess nuvarande tilldelning (`GET /2281/restaurant-numbers/{nummer}/assignment`). Saknas numret, eller gäller tilldelningen ett annat organisationsnummer än tillståndshavarens, granskas tillståndet (G11). |
| SupportManagement | Lagrar numret                 | Som parameter på intressenten för serveringsstället (avsnitt 7.2.1)                                                                                                                                                           |
| PartyAssets       | Lagrar numret                 | Som `additionalParameter` på tillståndet (avsnitt 7.4)                                                                                                                                                                        |

Migreringen skapar inga nummer eller tilldelningar i licensed-business. Registret fylls av importen, och avvikelser rättas genom registervård i AlkT före migreringen (beslut 9). Annars skulle två vägar in i registret kunna ge dubbla tilldelningar.

**Samma lagring som nya tillstånd (beslut 18, valt 2026-10-05).** Numret lagras som parameter på intressenten för serveringsstället i SupportManagement och som parameter på tillståndet i PartyAssets. Det finns inte i pw-alkt ännu men läggs till där, och migreringen gör likadant. Rollen och nycklarna måste vara desamma i pw-alkt och migreringen, annars går nya och migrerade ärenden inte att söka fram tillsammans. Förslaget är nyckeln `restaurantNumber` på båda ställena, i linje med pw-alkts engelska nycklar (`errandId`, `conditions`). Den slutliga rollen och nyckeln tas från pw-alkt när det är bestämt där.

licensed-business kontrollerar inte att numret har 8 siffror; det gör migreringen. Uppslaget i licensed-business slogs samman 2026-09-16, men om det är driftsatt i test och produktion är okänt.

### 7.7 Tillsynsärenden (beslut 1)

Beslut 1 tar med tillsynsärendena i den första migreringen. Tillsynen i AlkT är inte analyserad i detalj än. Avsnittet beskriver vad som är känt, hur ärendena ska se ut och vad som återstår innan mappningen kan bli klar. Tobaksdatabasens tillsyn beskrivs i 7.8.6.

**Vad AlkT innehåller** (hela databasen, analys omgång 2 och 4):

|                    Del                    |                 Antal                  |                                                                               Innehåll                                                                               |
|-------------------------------------------|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Ärendetyp 07 "Tillsyn, ifrågasatt åtgärd" | 162, varav 8 öppna                     | Utredning av brister. Kan leda till beslut om erinran, varning, skärpta villkor eller återkallelse.                                                                  |
| Ärendetyp 08 "Tillsyn, restaurangrapport" | 51, varav 12 öppna                     | Uppföljning av restaurangrapporter                                                                                                                                   |
| Ärendetyp 09 "Tillsyn, information m m"   | 5, varav 1 öppet                       | Information                                                                                                                                                          |
| Beslut i tillsyn                          | 163                                    | Erinran 60, varning 58, återkallat tillstånd 22, ej åtgärd 21, skärpta villkor 2                                                                                     |
| Tillsynsbesök (`Tillsyn_Tillfälle`)       | 1 554                                  | Besök med tillsynsart (rutintillsyn, uppföljningstillsyn, påkallad tillsyn, Mysam) och 1 002 anmärkningar. Besöken hör till serveringsstället, inte till ett ärende. |
| Filer från tillsyn                        | 745 på serveringsställen med tillstånd | PDF 452, doc 241, övrigt                                                                                                                                             |

**Så blir ett tillsynsärende i SupportManagement.**
- Ett avslutat ärende per tillsynsärende i AlkT. Det får etiketterna för tillsyn i ALKT plus etiketten "Migrerat ärende", som spärrar processer också här (avsnitt 7.3.2).
- Intressenter: serveringsstället, med serveringsställenumret som parameter (avsnitt 7.2.1), och den tillståndshavare som ärendet gällde, med uppslag i Party som i 7.2.
- Parametrar: ärendetyp, diarienummer, öppnat och avslutat datum, beslutet i tillsynsärendet (typ och datum) och anmärkningar.
- Diarienumret ligger i den externa taggen `diarienummer`, precis som för tillstånden (beslut 4).
- Ärendet kopplas till ärendet för serveringsställets migrerade tillstånd med en relation, när serveringsstället har ett sådant.
- Filerna i tillsynsärendet följer med enligt samma princip som för tillstånden (beslut 7).
- Inga tillstånd skapas eller ändras i PartyAssets.

**Det som återstår innan mappningen är klar.**
1. Analys omgång 6: hur många tillsynsärenden och tillsynsbesök som gäller serveringsställen med och utan gällande tillstånd, vilka år de spänner över, hur ägare och diarienummer är ifyllda, och filerna per ärende.
2. Avgränsning: om alla tillsynsärenden tas med eller bara de på serveringsställen med gällande tillstånd, och om tillsynsbesöken blir egna ärenden, uppgifter på tillsynsärendet eller bara datum för senaste tillsyn på serveringsstället.
3. Vilka etiketter i ALKT som tillsynsärendena ska ha.
4. Vilka personer som följer med. Serveringspersonal vid besöken finns med personnummer i `Tillsyn_Servpers`. Enligt beslut 15 följer personal inte med.

### 7.8 Tobak (fas 2)

Tobaksdatabasen ger tre sorters ärenden: tobakstillstånd, anmälningar och tillsynsärenden. Flödet, spärrarna och märkningen är desamma som för AlkT (avsnitt 6, 7.3.1, 7.3.2 och 9), och granskningen följer 7.1. Källan i statustabellen är TOBAK.

#### 7.8.1 Urval

|                  Vad                   |                                                                        Urval                                                                        |              Antal 2026-10-06              |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| Tobakstillstånd                        | Försäljningsställen i vyn `FS_GT_Tobak`. Granskningsreglerna avgör vilka som migreras (7.1.1).                                                      | 78, varav 67 på aktiva försäljningsställen |
| Anmälan om folköl                      | Aktiva försäljningsställen med markeringen `OLTYP`                                                                                                  | 78                                         |
| Anmälan om e-cigaretter                | Aktiva försäljningsställen med markeringen `ECIGTYP`                                                                                                | 52                                         |
| Anmälan om tobaksfria nikotinprodukter | Aktiva försäljningsställen med markeringen `TobaksfriNikotinTyp`                                                                                    | 62                                         |
| Tillsynsärenden                        | Enligt 7.8.6                                                                                                                                        | 32 och 2 om inre tillsyn                   |
| Ingår inte                             | Upphörda försäljningsställen utan tillstånd, aktiva försäljningsställen utan produkttyp (18), nikotinläkemedel (finns inte), avgifter och statistik | –                                          |

Appen läser vyn i stället för att räkna fram tillstånden själv, så att urvalet blir detsamma som i systemet. Vyns logik syns inte för vårt konto. G17 och G18 fångar det vi vet att vyn inte tar hänsyn till. Får kontot behörigheten VIEW DEFINITION kan logiken dokumenteras och reglerna stämmas av mot den.

#### 7.8.2 Tillståndshavare och försäljningsställe

Tillståndshavaren är försäljningsställets ägare (`FörsäljningsStällen.Agarid` → `Ägare`). Nummer, namn, adress och kontaktuppgifter mappas som i 7.2, eftersom `Ägare` har samma kolumner som i AlkT. Kopian av ägarens uppgifter på försäljningsstället används bara för att hitta avvikelser (G20). Personer med betydande inflytande hämtas från `PBI_ägare` enligt beslut 15, med rollerna i bilaga B.

Försäljningsstället blir en egen intressent i ärendet, på samma sätt som serveringsstället (7.2.1):

|      Källa (`FörsäljningsStällen`)      |      Mål på intressenten       |                                               Regel                                               |
|-----------------------------------------|--------------------------------|---------------------------------------------------------------------------------------------------|
| –                                       | `role`                         | Rollen för försäljningsställe. Rollen måste finnas i ALKT.                                        |
| –                                       | `externalId`, `externalIdType` | Lämnas tomma. Ett försäljningsställe är ingen part i Party.                                       |
| `Namn`                                  | `organizationName`             | –                                                                                                 |
| `GatuAdress`, `PostNummer`, `PostOrt`   | `address`, `zipCode`, `city`   | –                                                                                                 |
| `Telefon1`, `Email`                     | Kontaktkanaler PHONE och EMAIL | Tomma värden utelämnas                                                                            |
| `Kategori` med kodtabellen `Kategorier` | Parameter `kategori` (förslag) | Text, till exempel "Livsmedelsbutik, butikskedja". 12 aktiva försäljningsställen saknar kategori. |
| `Område` med kodtabellen `Områden`      | Parameter `omrade` (förslag)   | Text, till exempel "City" eller "Timrå" (beslut 19)                                               |

Tobaksdatabasen har inga serveringsställenummer, så avstämningen mot licensed-business (7.6, G6 och G11) görs inte.

#### 7.8.3 Ärende för tobakstillstånd

Ärendet får samma fält som i 7.3, med dessa skillnader:

|       Fält       |                                                           Värde                                                           |
|------------------|---------------------------------------------------------------------------------------------------------------------------|
| `title`          | "Tobakstillstånd – {försäljningsställets namn}"                                                                           |
| `description`    | En läsbar sammanfattning av tillståndstyp, försäljningsställe, utfärdandedatum och vilka andra produkter som är anmälda   |
| `classification` | Kategori och typ för befintliga tobakstillstånd (beslut 5)                                                                |
| `labels`         | Samma etiketter som ett vanligt ärende om tobakstillstånd, plus "Migrerat ärende"                                         |
| `externalTags`   | `diarienummer` = diarienumret i det beviljande ärendet, nyckeln mot tobaksdatabasen och skyddet mot dubbletter (beslut 4) |
| `stakeholders`   | Tillståndshavaren, försäljningsstället och, enligt beslut 15, personer med betydande inflytande                           |

Parametrar, med samma regler som i 7.3.3:

|   Nyckel (förslag)    |          Visningsnamn          |                                                         Källa                                                         |                                          Format                                          |
|-----------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `tobakFsId`           | Id i tobaksdatabasen           | `FSID`                                                                                                                | Tal                                                                                      |
| `tillstandstyp`       | Tillståndstyp                  | Vyns kolumner `GTDE`, `GTPH` och `GTDI`                                                                               | "Detaljhandel", "Partihandel" eller "Distanshandel". I dag finns bara detaljhandel.      |
| `utfardat`            | Utfärdat                       | Det senaste beviljande beslutets `GiltigtFROM_Datum`, annars `BeslutsDatumTid`                                        | ÅÅÅÅ-MM-DD                                                                               |
| `diarienummer`        | Diarienummer i tobaksdatabasen | Det beviljande ärendets `DiarieNr`                                                                                    | Som det står. Samma värde ligger i den externa taggen och på tillståndet.                |
| `tidigareBeviljanden` | Tidigare beviljanden           | Äldre beviljande beslut på samma försäljningsställe                                                                   | "ÅÅÅÅ-MM-DD, diarienummer", ett värde per beslut                                         |
| `senasteBeslut`       | Senaste beslut                 | Det senaste beslutet på försäljningsstället, om det är senare än beviljandet, till exempel en godkänd ändring i bolag | "Beslutstyp, beslutsdatum"                                                               |
| `anmaldaProdukter`    | Anmälda produkter              | Markeringarna för folköl, e-cigaretter och tobaksfria nikotinprodukter                                                | Ett värde per produkt. Anmälningarna blir också egna ärenden (7.8.5).                    |
| `noteringar`          | Noteringar                     | `FörsäljningsStällen.Notering`                                                                                        | Text. Fältet rymmer 6 000 tecken och delas upp i flera värden om det är längre än 3 000. |
| `migrerad`            | Migrerad                       | –                                                                                                                     | "ÅÅÅÅ-MM-DD från tobaksdatabasen"                                                        |

#### 7.8.4 Tobakstillstånd i PartyAssets (beslut 20)

pw-alkt skapar inga tobakstillstånd, så det finns ingen förlaga. Förslaget följer 7.4:

|             Fält             |                                                                 Värde                                                                  |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `assetId`                    | Ärendets id                                                                                                                            |
| `origin`                     | SUPPORTMANAGEMENT                                                                                                                      |
| `partyId`                    | Tillståndshavaren, från Party                                                                                                          |
| `type`                       | Samma typ som för serveringstillstånden (beslut 20). Inte PERMIT.                                                                      |
| `title`                      | Samma namn som AoT-lösningen ger tobakstillstånd. Finns inget sådant än är förslaget "Tillstånd för tobaksförsäljning – detaljhandel". |
| `description`                | "Tobakstillstånd för {försäljningsställets namn}"                                                                                      |
| `issued`                     | Som `utfardat` i 7.8.3                                                                                                                 |
| `validTo`                    | Utelämnas. Tillstånden gäller tills vidare.                                                                                            |
| `status`                     | ACTIVE, eller EXPIRED när undantagslistan säger "migrera som utgånget"                                                                 |
| Länk till ärendet och header | Som i 7.4                                                                                                                              |

`additionalParameters`, högst 255 tecken per värde: `errandId`, `forsaljningsstalle` (namnet), `tillstandstyp`, `diarienummer` och `migreradFran` = "Tobaksdatabasen".

#### 7.8.5 Anmälningar (beslut 21 och 22)

Varje anmälan blir ett avslutat ärende per försäljningsställe och produkt, eftersom nya anmälningar kommer in som ett ärende per produkt. Det ger 192 ärenden. Varje anmälan får också en post i PartyAssets, kopplad till ärendet på samma sätt som ett tillstånd (beslut 21). Flödet är detsamma som i avsnitt 6.

Ärendet:

|            Fält            |                                                                                                                    Värde                                                                                                                    |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `title`                    | "Anmälan om {produkt} – {försäljningsställets namn}", till exempel "Anmälan om försäljning av folköl – ..."                                                                                                                                 |
| `classification`, `labels` | Samma som ett vanligt anmälningsärende av samma slag, plus "Migrerat ärende"                                                                                                                                                                |
| `externalTags`             | `tobakAnmalan` = "{FSID}-{produkt}", till exempel "1234-FOLKOL" (beslut 22). Dessutom `diarienummer` när anmälan har ett.                                                                                                                   |
| `stakeholders`             | Tillståndshavaren och försäljningsstället, som i 7.8.2. Inga personer med betydande inflytande.                                                                                                                                             |
| Parametrar                 | `tobakFsId`, `produkt`, `anmald`, `anmaldUppskattat`, `diarienummer` (från det senaste anmälningsärendet, annars från den senaste anmälningshändelsen) och `migrerad`. `diarienummer` utelämnas när anmälan bara finns som markering (G22). |

Posten i PartyAssets:

|             Fält             |                                                                                                                                        Värde                                                                                                                                        |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `assetId`                    | Ärendets id                                                                                                                                                                                                                                                                         |
| `origin`                     | SUPPORTMANAGEMENT                                                                                                                                                                                                                                                                   |
| `partyId`                    | Tillståndshavaren, från Party                                                                                                                                                                                                                                                       |
| `type`                       | Enligt beslut 20                                                                                                                                                                                                                                                                    |
| `title`                      | Samma namn som AoT-lösningen ger anmälan. Finns inget sådant än är förslaget "Anmälan om försäljning av folköl", "Anmälan om servering av folköl", "Anmälan om försäljning av e-cigaretter och påfyllningsbehållare" eller "Anmälan om försäljning av tobaksfria nikotinprodukter". |
| `description`                | "{titeln} på {försäljningsställets namn}"                                                                                                                                                                                                                                           |
| `issued`                     | `anmald`, se nedan. Fältet är obligatoriskt i PartyAssets.                                                                                                                                                                                                                          |
| `validTo`                    | Utelämnas                                                                                                                                                                                                                                                                           |
| `status`                     | ACTIVE                                                                                                                                                                                                                                                                              |
| `additionalParameters`       | `errandId`, `forsaljningsstalle`, `produkt`, `diarienummer` när det finns, `anmaldUppskattat` när datumet är uppskattat och `migreradFran` = "Tobaksdatabasen"                                                                                                                      |
| Länk till ärendet och header | Som i 7.4                                                                                                                                                                                                                                                                           |

Filerna läggs bara på ärendet, eftersom anmälningarna inte har några beslutsdokument.

**Datum för anmälan (`anmald`).** Datumet tas från den första registrerade anmälan, alltså anmälningsärendets öppningsdatum eller anmälningshändelsens datum. 63 anmälningar finns bara som markering (G22). De får försäljningsställets tidigaste kända datum, från den första händelsen eller det första ärendet, och `anmaldUppskattat` = "ja". Har försäljningsstället inga händelser eller ärenden alls granskas anmälan (G23), eftersom PartyAssets kräver ett datum.

Produkterna och var anmälan syns:

|                Produkt                |       Markering       | Anmälningsärende | Anmälningshändelse |
|---------------------------------------|-----------------------|------------------|--------------------|
| Försäljning av folköl                 | `OLTYP`               | `Fföl`           | 014 och `BeaFol`   |
| Servering av folköl                   | `OLTYP`               | `SÖL`            | 015                |
| E-cigaretter och påfyllningsbehållare | `ECIGTYP`             | `ec`             | 105 och `BekAnm`   |
| Tobaksfria nikotinprodukter           | `TobaksfriNikotinTyp` | `tn`             | 047                |

Om en anmälan om folköl gäller försäljning eller servering avgörs av den senaste anmälan av folköl. Finns ingen avgörs det av kategorin: kategorierna för serveringsställen räknas som servering och övriga som försäljning.

Diarienumret kan inte vara nyckel för anmälningarna: 63 anmälningar saknar diarienummer, och samma diarienummer används för upp till fem butiker i samma kedja. Nyckeln är därför försäljningsställets id och produkten (beslut 22).

#### 7.8.6 Tillsyn

Tillsynsärendena ingår enligt beslut 1 och följer samma princip som i AlkT (7.7). Tobaksdatabasen har 32 tillsynsärenden (`Tills`), 2 ärenden om inre tillsyn (`IT`), 331 tillsynsbesök och 96 anmärkningar, till exempel påträngande reklam, kontrollköp och brister i egentillsynsprogrammet. Besöken har en tillsynsart: tillsynsbesök enligt plan, kontrollköp, påkallad tillsyn eller tillsynsprotokoll. Besökens markeringar för produkttyp är aldrig ifyllda. Avgränsningen och mappningen görs tillsammans med AlkT:s tillsyn (7.7, punkt 1–4).

#### 7.8.7 Filer

Enligt beslut 7 följer filerna i det beviljande ärendet med: 488 filreferenser för de 78 tillstånden, varav 269 dokument som systemet har skapat och 219 bilagor. Ett beviljande ärende saknar filer. För anmälningarna följer filerna i anmälningsärendet med, när det finns (beslut 21). Filerna hämtas från filarean på namn, som för AlkT (3.5 och 13).

## 8. Statustabell, rapport och spårbarhet

Statustabellen har en rad per källtillstånd.

|                   Kolumn                   |                                                 Innehåll                                                 |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Källa                                      | ALKT eller TOBAK                                                                                         |
| Käll-id                                    | `ObjektID` i AlkT, eller `FSID` i tobaksdatabasen. För anmälningar `FSID` och produkten.                 |
| Status                                     | NY, GRANSKAS, TILLSTANDSHAVARE_KLAR, ARENDE_SKAPAT, TILLSTAND_SKAPAT, KLAR eller FEL                     |
| Orsak                                      | Granskningsregel (G1–G23) och detalj, eller vad som gick fel, utan namn eller nummer                     |
| Ärende-id och ärendenummer                 | Från SupportManagement                                                                                   |
| Tillståndets id och PartyAssets interna id | `assetId` och id:t ur `Location`. Det interna id:t behövs för att hämta, ändra eller radera tillståndet. |
| Körning och tidpunkter                     | För spårbarhet                                                                                           |

Rapporten efter varje körning visar antal per status och en lista med en rad per tillstånd:
- käll-id,
- serveringsställenummer och serveringsställets namn, eller försäljningsställets namn,
- status och, för tillstånd som granskas, regel och detalj.

Serveringsställets nummer och namn finns med för att verksamheten ska kunna hitta tillståndet i AlkT; käll-id syns inte i AlkT:s gränssnitt. Rapporten innehåller aldrig tillståndshavarens namn, organisationsnummer eller personnummer. En enskild firmas organisationsnummer är ett personnummer.

Undantagslistan ligger i samma databas som statustabellen och innehåller käll-id, utfall, vem som beslutade och när.

## 9. Spärrar mot mail och processer

SupportManagement har ingen tyst import, så varje sidoeffekt stoppas för sig.

|                                                                                                        Regel                                                                                                        |                                                                                                         Vad den stoppar                                                                                                         |                                                 Grund i koden (SupportManagement)                                                 |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| R1. Alla anrop till SupportManagement skickar `X-Trigger-Process: false` och `X-Sent-By: alkt-migration; type=migration`. Anropen till PartyAssets skickar samma `X-Sent-By`.                                       | Processhändelser för migreringens egna anrop. Headern gäller inte för AD-konton.                                                                                                                                                | `ProcessEventPublisher.java` rad 220 och 264                                                                                      |
| R2. Etiketten "Migrerat ärende" spärrar alla processhändelser för ärendet, även startkommandot, och kan inte tas bort av handläggare. Det kräver en ändring i SupportManagement (avsnitt 7.3.2, beslut 17).         | Processer som startar när en handläggare senare ändrar ärendet, öppnar det igen eller trycker på "Starta process"                                                                                                               | `docs/alkt-processintegration.md` rad 2484, `ProcessEventPublisher.java` rad 226 och 337–348                                      |
| R3. Ingen handläggare tilldelas, och ALKT har inga prenumerationer på hela namespacet under körningen.                                                                                                              | Interna notiser. När ett ärende aktiveras skrivs en notis till alla aktiva prenumerationer, även på hela namespacet, oavsett om någon är tilldelad. Det blir interna notiser, inte mail eller sms; de kanalerna är inte byggda. | `EventService.java` rad 172–175 och 217–221, `SubscriptionRepository.java` rad 54, `NotificationChannelDispatcher.java` rad 30–33 |
| R4. Migreringen anropar aldrig kommunikation, konversationer eller beslut.                                                                                                                                          | Mail och sms till kunden, och beslut som kräver AD-konto eller pw-alkt                                                                                                                                                          | `CommunicationService.java`, `DecisionValidator.java` rad 99                                                                      |
| R5. Ärenden skapas som utkast och aktiveras sist.                                                                                                                                                                   | Åtgärder, notiser och processer medan ärendet byggs                                                                                                                                                                             | `ErrandActionService.java` rad 120                                                                                                |
| R6. De automatiska åtgärderna i ALKT stängs av under körningen. Före körningen kontrolleras också att ingen åtgärd som reagerar på ändringar har villkor som matchar de migrerade ärendenas status eller etiketter. | Mail som skickas när ett ärende skapas eller aktiveras, och mail eller etikettändringar första gången en handläggare ändrar ett migrerat ärende efter att åtgärderna slagits på igen                                            | `ErrandActionService.java` rad 137–161, `SendEmailAction.java` rad 47 och 136–145                                                 |
| R7. Aktiva ärenden raderas aldrig.                                                                                                                                                                                  | Borttagningshändelser till pw-alkt                                                                                                                                                                                              | `ProcessEventPublisher.java`                                                                                                      |

Avstängningen i R6 gäller hela namespacet. Ärenden som skapas på vanligt sätt under körningen får alltså inga automatiska åtgärder. Körningen läggs därför vid en tid när få ärenden kommer in.

PartyAssets skickar varken händelser eller meddelanden. Länken till ärendet skrivs till relationstjänsten; att den inte skickar något har vi inte kunnat kontrollera i kod.

**Kontroller före körning, i varje miljö:**
- inga aktiva automatiska åtgärder för ALKT,
- inga åtgärder som reagerar på ändringar och har villkor som matchar migrerade ärenden,
- inga prenumerationer på hela namespacet ALKT, eller ett beslut att interna notiser accepteras,
- etiketten "Migrerat ärende" finns och har attributet som spärrar processer,
- ett testärende med etiketten skriver ingen processhändelse när det ändras eller när "Starta process" används,
- statusen, rollerna och klassificeringen finns i ALKT,
- PartyAssets kräver inget skäl för statusarna ACTIVE och EXPIRED.

**Kontroller efter körning,** samma dag, som ska vara noll för de migrerade ärendena:

|          Tabell           | Hur ärendena hittas |                                     Att tänka på                                      |
|---------------------------|---------------------|---------------------------------------------------------------------------------------|
| `process_event_outbox`    | Ärende-id           | Levererade rader raderas när de är äldre än ett dygn, så kontrollen görs samma dag.   |
| `errand_process`          | Ärende-id           | –                                                                                     |
| `notification`            | Ärende-id           | Notiser till tilldelad handläggare                                                    |
| `subscriber_notification` | Ärende-id           | Notiser till prenumeranter                                                            |
| `errand_action`           | Ärende-id           | Bara åtgärder som väntar. En åtgärd som körs direkt syns i stället i `communication`. |
| `communication`           | Ärendenummer        | Tabellen saknar ärende-id.                                                            |

Dessutom ska varken pw-alkt eller Messaging ha tagit emot något från migreringen.

## 10. Körning

### 10.1 Förutsättningar

- Etiketter, status, roller (även rollen för serveringsställe) och klassificering finns i ALKT i test och produktion, även etiketterna för tillsynsärenden (avsnitt 7.7).
- pw-alkt har bestämt roll och nycklar för serveringsställenumret (beslut 18).
- Mappningen av tillsynsärendena är klar (avsnitt 7.7).
- Klientnycklar finns för SupportManagement, PartyAssets, Party och licensed-business i test och produktion.
- Konton med bara läsrätt finns till AlkT och tobaksdatabasen.
- Rollen för försäljningsställe, och klassificering och etiketter för tobakstillstånd och anmälningar, finns i ALKT i test och produktion.
- Beslut 20 är taget.
- Utvecklardatorn har VPN-åtkomst till AlkT:s SQL Server och till API-gatewayen i test och produktion (beslut 10), och läsåtkomst till filarean.
- Katalogerna på filarean är angivna i den lokala `.env` (avsnitt 5.1).
- Serveringsställenumren är inlästa i licensed-business, och uppslaget är driftsatt.
- Spärren mot processer i SupportManagement (beslut 17) är driftsatt i test och produktion.

### 10.2 Ordning

1. Provkörning mot testmiljön. Den ger granskningslistan.
2. Verksamheten går igenom listan, gör registervård i AlkT och för in undantag (listan *Att hantera före migrering*). Provkörningen upprepas tills alla tillstånd på listan är hanterade. 135 tillstånd är få nog för att verksamheten kan gå igenom hela rapporten. Detsamma gäller tobaksdatabasens 78 tillstånd och 192 anmälningar.
3. Skarp körning i testmiljön, följd av kontrollerna i avsnitt 9 och stickprov i handläggarnas gränssnitt.
4. Analyserna av AlkT och tobaksdatabasen körs om, eftersom flera regler beror på dagens datum.
5. Alla pågående ärenden i AlkT och tobaksdatabasen avslutas eller flyttas för hand (beslut 16 och 23).
6. De automatiska åtgärderna i ALKT stängs av i produktion.
7. Provkörning i produktion, direkt före den skarpa körningen. Granskningslistan får bara innehålla tillstånd i undantagslistan.
8. Skarp körning i produktion och kontrollerna i avsnitt 9, samma dag.
9. De automatiska åtgärderna slås på igen.
10. Verksamheten godkänner resultatet.

AlkT låses inte (beslut 11). Från och med produktionskörningen är SupportManagement och PartyAssets facit, och ändringar som görs i AlkT därefter följer inte med. Verksamheten informeras om det före körningen.

### 10.3 Återställning

- Utkast kan raderas utan sidoeffekter.
- Aktiva ärenden raderas inte (R7). Om ett ärende ändå måste bort skickas en borttagningshändelse till pw-alkt. Den gör ingenting eftersom ärendet saknar process, men den ska vara känd i förväg.
- Tillstånd i PartyAssets raderas via API:et med det interna id:t. Raderingen är slutgiltig, och relationen till ärendet ligger kvar i relationstjänsten.
- Statustabellens rad återställs, och tillståndet körs om.

## 11. Test och kvalitetssäkring

- **Enhetstester för reglerna:** granskningsreglerna G1–G23, tolkning av säsonger, perioder, tider och nummer, uppbyggnad av parametrar, beskrivning och tillståndets parametrar med längdgränsen 255 tecken.
- **Integrationstester:**
  - målsystemen ersätts med WireMock,
  - filarean ersätts med en testkatalog med påhittade filer,
  - källan är en SQL Server i Testcontainers med AlkT:s och tobaksdatabasens tabellstruktur, samma kompatibilitetsnivåer (under 110 och 110) och påhittade rader, aldrig riktiga data.
- **Testerna kräver att:**
  - varje anrop till SupportManagement har `X-Trigger-Process: false` och rätt `X-Sent-By`,
  - sökningen efter befintligt ärende anger `lifecycle`,
  - inga anrop görs till kommunikation, konversationer eller beslut,
  - inget skrivs, flyttas eller raderas i filareans kataloger under en körning,
  - en omkörning efter ett avbrott i varje steg inte skapar dubbletter,
  - ett diarienummer som finns i både AlkT och tobaksdatabasen inte gör att ett ärende från den andra källan återanvänds.
- **Tester i SupportManagement för spärren:**
  - ett ärende med en spärretikett skriver ingen processhändelse, oavsett händelsetyp, övriga etiketter och status,
  - startkommandot och signaler avvisas för ett sådant ärende,
  - ett AD-konto kan inte ta bort etiketten,
  - ärenden utan spärretikett påverkas inte.
- **Täckningskrav:** samma som i övriga tjänster.

## 12. Säkerhet och personuppgifter

- Källdatabaserna läses med konton som bara har läsrätt, och appen har en egen datakälla för dem. Databasmigreringar och JPA får aldrig peka mot källdatabaserna.
- Migreringen körs på en utvecklardator (beslut 10). Därför gäller följande:
  - nycklar och lösenord anges som miljövariabler vid körningen och sparas aldrig i filer eller git,
  - datorn har krypterad disk och lämnas inte obevakad under körningen,
  - produktionsnycklarna byts eller spärras när migreringen är godkänd,
  - den lokala databasen innehåller inga personuppgifter.
- Anslutningen till SQL Server är krypterad, och serverns certifikat läggs i Javas truststore.
- Loggar, rapporter, statustabell och undantagslista innehåller inga namn eller nummer på tillståndshavare eller andra personer.
- Ingen export eller kopia av källdatabaserna görs.
- Filarean läses bara. Appen öppnar filerna bara för läsning och skriver, flyttar eller raderar aldrig något där.
- Repot är publikt. Sökvägar och servernamn för filarean och databaserna checkas därför inte in; de anges som miljövariabler eller i en lokal `.env` som git ignorerar.
- Analysen delas bara som tabellstruktur och sammanräknade siffror.
- `X-Sent-By` skickas till både SupportManagement och PartyAssets, så att migreringen syns som aktör i båda systemens historik.

## 13. Fas 2: tobakstillstånd och dokument

**Tobak.** Tobaksdatabasen analyserades 2026-10-06 (3.4), och mappningen står i 7.8. Tobakstillstånden och anmälningarna får poster i PartyAssets med en typ som bestäms i beslut 20.

**Dokument.** Enligt beslut 7 följer filerna i det ärende som utfärdade tillståndet med, plus serveringsställets egna filer:
- **Urval.** Tillståndet kopplas till sitt utfärdandeärende via beslutet det pekar på eller via utfärdandediarienumret. 113 av 135 tillstånd får en koppling, och 18 av dem kopplas till mer än ett ärende; då följer filerna från alla de ärendena med. De 22 utan koppling listas (G13), så att verksamheten kan leta fram dokument för hand om de behövs.
- **Omfång.** Cirka 2 060 filer: cirka 980 bilagor (mest PDF, 87 Outlook-mejl), cirka 1 050 dokument som AlkT har skapat (RTF) och 13 filer på serveringsställena. Ritningarna på tillstånden saknar filändelse och är troligen beteckningar, inte filer. Det kontrolleras på filarean.
- **Matchning.** Databaserna sparar bara filnamn, och katalogerna på filarean är platta (3.5). Filerna hämtas på namn från den katalog som hör till referensen. Namnen är nästan unika (10 234 referenser på 10 112 namn i AlkT). En fil som inte hittas, eller som är större än 50 MB, noteras i rapporten (G14).
- **Var filerna läggs.** Alla filer läggs på ärendet. Beslutsdokumenten (68 AlkT-dokument och 38 bilagor) läggs också på tillståndet i PartyAssets, eftersom pw-alkt lägger beslutets bilagor på tillstånden.
- **Att tänka på:**
- Uppladdning till ett aktivt ärende är säker enligt R1–R3, men ger en händelse i ärendets historik.
- Uppladdning till ett aktivt tillstånd ger en ny version av tillståndet.
- Tillstånd med status EXPIRED tar inte emot bilagor.
- Varje fil får vara högst 50 MB i SupportManagement.

Om dokumenten ska följa med från början bör produktionskörningen vänta på fas 2 (beslut 12).

## 14. Beslut

Beslut 1–18 togs 2026-10-05 och står också på sidan "Migrering av alkohol- och tobakstillstånd". Beslut 19–23 kommer från analysen av tobaksdatabasen; 19, 21, 22 och 23 togs 2026-10-06, och 20 är öppet. Beslut 3 ändrades samma dag: typen i PartyAssets blir inte PERMIT. De nya och ändrade besluten behöver föras in på sidan. Fas 1 är de 135 serveringstillstånden och tillsynsärendena i AlkT; fas 2 är tobakstillstånden, anmälningarna och dokumenten.

|                                                  Beslut                                                  |                                                                                                           Valt beslut                                                                                                            |    Beskrivs i     |
|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| **1. Omfattning: vad migreras?**                                                                         | Tillstånd och tillsynsärenden, från början. Gällande tillstånd och anmälningar med handlingarna från ärendet där tillståndet beviljades, samt tillsynsärendena.                                                                  | 2.3, 7.7          |
| **2. Beslut i SupportManagement** Om de migrerade ärendena får ett formellt beslut i det nya systemet.   | Nej, inga beslut skapas. Beslutsuppgifterna sparas på ärendet och i tillståndet.                                                                                                                                                 | 7.3.3, 7.4        |
| **3. Tillstånden i PartyAssets** Hur de migrerade tillstånden ser ut i registret som syns på Mina sidor. | Som tillstånd som beviljas i det nya systemet, med samma typ och samma nycklar som pw-alkt. Typen blir inte PERMIT och bestäms i beslut 20 (ändrat 2026-10-06).                                                                  | 7.4               |
| **4. Tillståndets id och ursprung**                                                                      | Migrerade tillstånd får samma namn som de som skapas i AoT-lösningen. Diarienumret lagras som parameter i PartyAssets och på ärendet, och används för att upptäcka dubbletter.                                                   | 6, 7.3, 7.4       |
| **5. Etiketter, status och klassificering**                                                              | Samma etiketter som vanliga ärenden, plus etiketten "Migrerat ärende", och en avslutad status.                                                                                                                                   | 7.3, 7.3.1        |
| **6. Ursprungliga datum och diarienummer**                                                               | Som uppgifter på ärendet. Diarienumret hanteras enligt beslut 4.                                                                                                                                                                 | 7.3.3             |
| **7. Bilagor**                                                                                           | Handlingarna från ärendet där tillståndet beviljades, cirka 2 060 filer. Tillstånd utan koppling till filer loggas.                                                                                                              | 3.2, 13           |
| **8. Tillståndshavare som saknas i Party**                                                               | Tillståndet loggas, och verksamheten utreder om det inträffar.                                                                                                                                                                   | 7.1.1 (G10)       |
| **9. Serveringsställenummer som saknas eller inte stämmer**                                              | Registervård i AlkT före migreringen.                                                                                                                                                                                            | 7.6               |
| **10. Var körningen sker**                                                                               | Migreringen körs på en utvecklardator.                                                                                                                                                                                           | 5.1, 12           |
| **11. När AlkT låses för ändringar**                                                                     | AlkT låses inte. Ändringar i AlkT efter migreringen följer inte med.                                                                                                                                                             | 10.2              |
| **12. När migreringen körs i produktion**                                                                | Före driftstarten av den nya handläggningen. Ska dokumenten följa med från början väntar körningen tills fas 2 är klar.                                                                                                          | 10.2, 13          |
| **13. Tillståndshavare när ägaren skiljer sig**                                                          | Serveringsställets nuvarande ägare. De 3 avvikelserna granskas.                                                                                                                                                                  | 7.2               |
| **14. Perioder och tidsbegränsade tillstånd**                                                            | Säsonger sparas som uppgift på ärendet. Slutdatum sätts bara när tillståndet inte gäller året runt och har en datumperiod. Passerade perioder granskas.                                                                          | 7.3.3, 7.4        |
| **15. Personer med betydande inflytande och personal**                                                   | Personer med betydande inflytande följer med som intressenter på ärendet. Personalen följer inte med.                                                                                                                            | 7.2               |
| **16. Pågående ärenden i AlkT**                                                                          | Alla 64 öppna ärenden avslutas i AlkT före produktionskörningen eller flyttas för hand.                                                                                                                                          | 10.2              |
| **17. Hur processer spärras**                                                                            | Spärretikett. Inga processer kan startas på migrerade ärenden.                                                                                                                                                                   | 7.3.2             |
| **18. Var serveringsställenumret lagras**                                                                | Som parameter på serveringsställets intressent och på tillståndet, likadant som pw-alkt kommer att göra.                                                                                                                         | 7.2.1, 7.6        |
| **19. Tillstånd och anmälningar i Timrå** Båda databaserna har området Timrå.                            | Samma kommun som AoT-lösningen använder för nya ärenden från Timrå, med området som parameter på serveringsstället eller försäljningsstället. Gäller 14 serveringstillstånd, 11 tobakstillstånd och 31 anmälningar.              | 3.2, 3.4.1        |
| **20. Typ i PartyAssets** Gäller tillstånd och anmälningar, för både alkohol och tobak.                  | Öppet. Det blir inte PERMIT. Typen bestäms tillsammans med AoT-lösningen. Övriga fält följer 7.4, 7.8.4 och 7.8.5.                                                                                                               | 7.4, 7.8.4, 7.8.5 |
| **21. Hur anmälningar migreras**                                                                         | Ett avslutat ärende per försäljningsställe och produkt, 192 ärenden, med filerna från anmälningsärendet. Varje anmälan får också en post i PartyAssets, kopplad till ärendet.                                                    | 7.8.5             |
| **22. Nyckel för anmälningar**                                                                           | Försäljningsställets id och produkten i den externa taggen `tobakAnmalan`, eftersom 63 anmälningar saknar diarienummer och samma diarienummer används för flera butiker. Tobakstillstånden använder diarienumret som i beslut 4. | 6, 7.8.5          |
| **23. Pågående ärenden i tobaksdatabasen**                                                               | Som beslut 16. De 47 öppna ärendena avslutas i tobaksdatabasen före produktionskörningen eller flyttas för hand. De står i listan *Att hantera före migrering* för tobak.                                                        | 3.4.2, 10.2       |

## 15. Risker

|                                Risk                                 |                                        Följd                                         |                                                                                       Åtgärd                                                                                        |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spärren i SupportManagement är inte driftsatt när ärendena migreras | Första ändringen av ett migrerat ärende startar en process                           | Spärren är en förutsättning för körningen (10.1) och kontrolleras med ett testärende (avsnitt 9)                                                                                    |
| Etiketten "Migrerat ärende" saknar spärrattributet i någon miljö    | Som ovan                                                                             | Kontroll före körning (avsnitt 9)                                                                                                                                                   |
| Automatiska åtgärder är inte avstängda                              | Mail skickas vid körningen                                                           | Kontroll före körning (R6)                                                                                                                                                          |
| En åtgärd som reagerar på ändringar matchar migrerade ärenden       | Mail skickas första gången en handläggare ändrar ett migrerat ärende                 | Kontroll före körning (R6)                                                                                                                                                          |
| Prenumerationer på hela namespacet finns                            | Interna notiser för varje migrerat ärende                                            | Kontroll före körning (R3)                                                                                                                                                          |
| Bolag saknas i Party, till exempel avregistrerade bolag             | Tillståndet kan inte skapas                                                          | Granskning (G10, beslut 8)                                                                                                                                                          |
| Ett värde i tillståndets parametrar är längre än 255 tecken         | Tillståndet kan inte sparas                                                          | Korta värden och test av längdgränsen (avsnitt 7.4)                                                                                                                                 |
| Migreringen ändrar något på filarean                                | Dokument i de gamla systemen skadas eller försvinner                                 | Appen öppnar filerna bara för läsning, ett test kräver att katalogerna är oförändrade efter en körning, och helst körs migreringen med ett konto som bara har läsrätt till filarean |
| Tillståndet visar omfattningen från ett tillägg som har upphört     | Fel uppgifter i det migrerade tillståndet                                            | Granskning (G3, G12)                                                                                                                                                                |
| Perioder och beslut passerar innan produktionskörningen             | Granskningslistan växer                                                              | Analyserna körs om före produktion (10.2)                                                                                                                                           |
| AlkT ändras under eller efter körningen                             | Ändringen följer inte med till de migrerade ärendena                                 | Accepterat (beslut 11). Körningen tar kort tid och görs utanför kontorstid, och verksamheten informeras om att AlkT inte längre är facit efter körningen.                           |
| Produktionsnycklar och data hanteras på en utvecklardator           | Nycklarna kan komma på avvägar om datorn tappas bort eller nycklarna sparas i en fil | Säkerhetskraven i avsnitt 12, och nycklarna byts eller spärras efter godkänd migrering                                                                                              |
| Diarienummer saknas eller finns på flera tillstånd                  | Dubblettskyddet fungerar inte för de tillstånden                                     | Granskning (G15, G16) och registervård i AlkT                                                                                                                                       |
| Tillsynsärendena är inte analyserade                                | Omfång och mappning kan bli större än väntat                                         | Analys omgång 6 och mappning innan bygget av den delen (avsnitt 7.7)                                                                                                                |
| Migrerade och nya tillstånd ser olika ut                            | Mina sidor och handläggarstödet visar dem olika                                      | Samordning med pw-alkt (beslut 3)                                                                                                                                                   |
| Uppslaget av serveringsställenummer är inte driftsatt               | Avstämningen kan inte göras                                                          | Beroende i planeringen                                                                                                                                                              |
| Filer saknas på filarean eller är större än 50 MB                   | Ärenden blir utan dokument, eller uppladdningen misslyckas                           | Matchningen mot filarean (3.5) visar det i förväg, och sådana filer listas (G14)                                                                                                    |
| Historiken bevaras inte när AlkT stängs                             | Uppgifter som ska arkiveras går förlorade                                            | Arkivleveransen bestäms innan AlkT stängs (avsnitt 3.5)                                                                                                                             |
| Skapat-datum blir migreringsdagen                                   | Handläggare kan tro att ärendet är nytt                                              | Etiketten "Migrerat ärende", parametrarna `utfardat` och `migrerad`, och information till handläggarna                                                                              |
| Tillstånden och anmälningarna i Timrå hamnar under fel kommun       | Ärendena hamnar hos fel organisation, och tillstånden visas fel på Mina sidor        | Beslut 19: samma kommun som AoT-lösningen. Kommunen måste vara känd före produktionskörningen.                                                                                      |
| Typen i PartyAssets är inte bestämd                                 | Tillstånd och anmälningar kan inte skapas, eller skapas med fel typ                  | Beslut 20 är en förutsättning för körningen (10.1)                                                                                                                                  |
| Vyn över gällande tobakstillstånd har logik som vi inte ser         | Urvalet kan ta med eller missa tillstånd                                             | G17 och G18 fångar de kända bristerna. Med behörigheten VIEW DEFINITION kan logiken dokumenteras.                                                                                   |
| Anmälningar som bara finns som markering gäller inte längre         | Ärenden skapas för anmälningar som har upphört                                       | G22 listar dem, och verksamheten går igenom dem före körningen                                                                                                                      |

## 16. Beroenden och öppna frågor

- Matchningen av filreferenserna mot filarean, och om bilagornas prefix i filnamnet finns med i databasens referenser (3.5).
- Om migreringen kan köras med ett konto som bara har läsrätt till filarean.
- Formatet på personnummer vid uppslag av PRIVATE i Party.
- Metadata i ALKT: status, etiketter, roller (även för personer med betydande inflytande) och klassificering.
- Om det finns prenumerationer på hela namespacet ALKT.
- Om uppslaget av serveringsställenummer i licensed-business är driftsatt.
- Vilken roll serveringsstället får som intressent och vilken nyckel serveringsställenumret får, när det läggs till i pw-alkt (beslut 18).
- Vad markeringen `ALP` i AlkT betyder.
- Om säsongerna på tillstånd som gäller året runt avser uteserveringen.
- Hur AlkT:s ärendehistorik och de dokument som inte migreras bevaras enligt dokumenthanteringsplanen, till exempel genom leverans till e-arkiv.
- Om anmälningarna som bara finns som markering på försäljningsstället fortfarande gäller (G22).
- Formatet på villkorens giltighetsdatum, och serveringstiderna i intervall 2–8.
- Tillsynsärendena: omfång, avgränsning, etiketter och personer (avsnitt 7.7).
- Vilket namn tillstånden får per tillståndstyp i AoT-lösningen, så att migrerade tillstånd får samma (beslut 4).
- Vilken typ tillstånd och anmälningar får i PartyAssets, för både alkohol och tobak (beslut 20).
- Vilket namn, vilken klassificering och vilka etiketter AoT-lösningen ger tobakstillstånd och anmälningar, och vilken roll försäljningsstället får.
- Vilken kommun AoT-lösningen använder för ärenden från Timrå (beslut 19). Blir det Timrås kommunkod (2262) behöver appen anropa SupportManagement och PartyAssets med olika kommun per ärende.
- Behörigheten VIEW DEFINITION på tobaksdatabasen, så att logiken i `FS_GT_Tobak` kan dokumenteras.
- Vad de 18 aktiva försäljningsställena utan produkttyp är.
- Om beslut 11, att källan inte låses, gäller även tobaksdatabasen.

## Bilaga A. Kodlistor ur AlkT som används

**Typ av serveringsställe (kodlista Q):**

| Kod |           Text           | Kod |            Text            |
|-----|--------------------------|-----|----------------------------|
| 1   | Discotek                 | 14  | Pausservering              |
| 3   | Dansrestaurang           | 15  | Kursgård                   |
| 4   | Föreningslokaler         | 16  | Vägkrog                    |
| 7   | Båtrestaurang            | 17  | Sportanläggning            |
| 8   | Hotellrestaurang         | 18  | Säsongsrestaurang          |
| 9   | Personal/lunchrestaurang | 19  | Folkets Hus/föreningslokal |
| 10  | Trafikrestaurang         | 20  | Restaurangtält             |
| 11  | Utpräglad matrestaurang  | 21  | Teater                     |
| 12  | Kvartersrestaurang       | 22  | Bryggeri                   |
| 13  | Värdshus                 | 23  | Evenemangslokal            |

Koderna UNG (ungdomlig målgrupp) och UTOK (utökad tillsyn) finns i samma kodlista men är markeringar, inte typer.

**Villkor (kodlista O):**
- 0100 Krav på bordsservering
- 0200 Krav från Räddningstjänst
- 0300 Krav på förordnade ordningsvakter

**Bolagstyp (kodlista S):**
- AB Aktiebolag
- EF Enskild firma
- FE Förening
- HB Handelsbolag
- KB Kommanditbolag
- KO Kommun
- SE Statlig enhet

**Roller för personer med betydande inflytande (kodlista P):** Bolagsman, Firmatecknare, Ledamot, Ordförande, Restaurangchef, Revisor, Suppleant, Verkställande direktör, Ägare.

## Bilaga B. Kodlistor ur tobaksdatabasen som används

**Beslutstyper (`Beslutskoder`):**

|         Kod         |                                                   Text                                                   |       Betydelse för migreringen       |
|---------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------|
| GTDE                | Tillstånd tobaksförsäljning – detaljhandel                                                               | Beviljande beslut                     |
| GTPH                | Tillstånd tobaksförsäljning – partihandel                                                                | Beviljande beslut, används inte i dag |
| GTDI                | Tillstånd tobaksförsäljning – distanshandel                                                              | Beviljande beslut, används inte i dag |
| GTDEU               | Upphörande av tillstånd på egen begäran                                                                  | Upphörande (G18)                      |
| GTDEÅ               | Återkallelse av försäljningstillstånd tobak                                                              | Upphörande (G18)                      |
| AÄDE                | Godkänd anmälan ändring i bolag                                                                          | Senare beslut, som upplysning         |
| GTDEA, GTDIA, GTPHA | Avslag på ansökan om detaljhandel, distanshandel och partihandel                                         | –                                     |
| FF, FFT             | Försäljningsförbud enligt lagen om tobak och liknande produkter och lagen om tobaksfria nikotinprodukter | Senare beslut, som upplysning         |
| VT                  | Varning enligt lagen om tobak och liknande produkter                                                     | –                                     |
| AI, AÖ, BESOVR      | Avslag inhibition, avslag överklagande, övrigt beslut                                                    | –                                     |

**Ärendetyper (`Ärendetyper`):** WADE ansökan om tillstånd tobak detaljhandel, WADI distanshandel, WAPH partihandel, WAAÄ anmälan om ändring i bolag, WAUP anmälan om upphörd försäljning, Fföl anmälan försäljning folköl, SÖL anmälan servering folköl, ec anmälan om försäljning av e-cigaretter och påfyllningsbehållare, tn anmälan om försäljning av tobaksfria nikotinprodukter, Tills tillsynsärende, IT inre tillsyn, Provk provköp, Uteb upphörande av tillstånd på egen begäran och Utk upphörande av tillstånd vid konkurs.

**Kategori på försäljningsställe (`Kategorier`):** Detaljhandel övrigt, Försäljning via internet, Kiosk, Livsmedelsbutik enskild, Livsmedelsbutik butikskedja, Serveringsställe grill/pizza, Serveringsställe övrigt, Serveringsställe personalmatsal, Serveringsställe butik och Spelbutik.

**Område (`Områden`):** 1 Granlo/Granloholm, 2 Njurunda, 3 Matfors, 4 Birsta/Sundsbruk, 5 Alnö, 6 Indal/Liden, 7 Stöde, 8 Haga/Skönsberg, 9 Nacksta, 10 Skönsmon, 11 Holm, 12 City och 13 Timrå.

**Bolagstyp (`Bolagstyper`):** AB aktiebolag, EF enskild firma, EK ekonomisk förening, FE ideell förening eller stiftelse, HB handels- eller kommanditbolag och OF kommun, stat eller landsting.

**Roller för personer med betydande inflytande (`PBI_Roller`):** Bolagsman, Enskild firma, Extern firmatecknare, Firmatecknare, Indirekt ägande, Ledamot, Ordförande, Platschef, Suppleant, VD och Ägarbolag.

## Bilaga C. Underlag

|                             Underlag                             |                                                        Var                                                         |
|------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Tabellstruktur, storlekar och analysresultat för AlkT            | Förvaras utanför repot                                                                                             |
| Tabellstruktur, storlekar och analysresultat för tobaksdatabasen | Förvaras utanför repot                                                                                             |
| Kartläggningen av filarean                                       | Förvaras utanför repot                                                                                             |
| Processintegrationen i SupportManagement                         | `api-service-support-management/docs/alkt-processintegration.md`                                                   |
| Hur pw-alkt bygger tillstånd i PartyAssets                       | `pw-alkt` (origin/main), `PartyAssetsMapper.java`                                                                  |
| Uppslag av partyId och skapande av tillstånd i PartyAssets       | `api-service-permit-loader`                                                                                        |
| Att hitta befintligt ärende via extern tagg                      | `api-service-sm-loader`, `SupportManagementService.java`. Sökningen där saknar `lifecycle` och hittar inte utkast. |
| Import av serveringsställenummer                                 | `api-service-licensed-business`, `ImportService.java`                                                              |

