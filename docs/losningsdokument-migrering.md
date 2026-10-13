# Lösningsdokument: Migrering av alkohol- och tobakstillstånd

|                             |                                                                                                                          |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Status                      | Alla beslut tagna 2026-10-05. Tillsynsärendena återstår att analysera och mappa (avsnitt 7.7).                           |
| Datum                       | 2026-10-05                                                                                                               |
| Författare                  | Andreas Carlsson                                                                                                         |
| Beslutslista                | Sidan "Migrering av alkohol- och tobakstillstånd" (https://claude.ai/code/artifact/c5ef17f8-e470-4c6e-80f2-76fbb5e5fdf6) |
| Verksamhetens förberedelser | Listan *Att hantera före migrering* med granskningsfrågan `granskningslista.sql`, som verksamheten får separat           |

## 1. Sammanfattning

Gällande alkohol- och tobakstillstånd och tillsynsärendena flyttas från de gamla systemen till SupportManagement och PartyAssets. Varje tillstånd blir ett avslutat ärende i SupportManagement och ett tillstånd i PartyAssets, och de två kopplas ihop. Tillsynsärendena blir avslutade ärenden i SupportManagement, och migreringsappen skapar en relation från tillståndsärendet till varje tillsynsärende som gällde samma serveringsställe och tillståndshavare. Migrerade ärenden får samma etiketter som vanliga ärenden plus etiketten "Migrerat ärende". Den etiketten spärrar processer, vilket kräver en liten ändring i SupportManagement.

Under migreringen skickas inga mail, sms eller meddelanden, och inga processer startar. Inga processer får heller starta senare, när handläggare ändrar de migrerade ärendena.

Arbetet görs av tjänsten `api-service-aot-loader`, en fristående Java-applikation som körs på en utvecklardator. Den läser källdatabaserna direkt med ett konto som bara har läsrätt och skriver allt via API:erna. Inget skrivs direkt i målsystemens databaser.

Migreringen görs i två faser:

| Fas |                                     Innehåll                                     |                                             Läge 2026-10-05                                              |
|-----|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 1   | 135 serveringstillstånd och tillsynsärendena ur AlkT                             | Tillstånden är analyserade och mappade. Tillsynsärendena återstår att analysera och mappa (avsnitt 7.7). |
| 2   | Tobakstillstånd och anmälningar ur en egen databas, och dokument från en filarea | Vi väntar på åtkomst till båda.                                                                          |

Beslut 1–24 är tagna, och beslut 25 är öppet. De står i avsnitt 14. Det verksamheten behöver rätta eller avsluta i AlkT före migreringen står i listan *Att hantera före migrering*, som verksamheten får separat.

## 2. Mål och avgränsning

### 2.1 Mål

Efter migreringen gäller följande:

- Varje gällande tillstånd finns som ett ärende i namespace ALKT (kommun 2281) som handläggarna kan söka fram och läsa. Ärendet innehåller tillståndets uppgifter och är märkt som migrerat.
- Varje gällande tillstånd finns i PartyAssets, tillhör tillståndshavaren och är kopplat till ärendet.
- Varje tillsynsärende som ingår finns som ett avslutat, migrerat ärende, kopplat till serveringsstället. Har serveringsstället ett migrerat tillstånd, och gällde tillsynsärendet samma tillståndshavare, finns en relation mellan tillståndsärendet och tillsynsärendet.
- Ingen kund har fått något meddelande, ingen process har startat, och inga mail har skickats.

### 2.2 Krav

| Nr |                                                                                                                              Krav                                                                                                                              |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| K1 | Inga mail, sms eller andra meddelanden skickas, varken under eller efter migreringen.                                                                                                                                                                          |
| K2 | Ingen process i pw-alkt startar, varken under migreringen eller när ett migrerat ärende ändras senare.                                                                                                                                                         |
| K3 | Migreringen kan köras om utan att det blir dubbletter, även efter ett avbrott mitt i ett tillstånd.                                                                                                                                                            |
| K4 | Det finns ett provkörningsläge som bara rapporterar vad som skulle skapas, utan att skriva något.                                                                                                                                                              |
| K5 | Varje migrerat tillstånd och tillsynsärende går att spåra från källrad till ärende och tillstånd, och tillbaka.                                                                                                                                                |
| K6 | Tillståndshavarens och andra personers namn, personnummer och organisationsnummer finns bara i källsystemen och målsystemen, aldrig i loggar, rapporter eller statustabeller. Undantag: filnamnen i rapporten (G14), som kan innehålla namn (valt 2026-10-12). |
| K7 | Källsystemen ändras inte av migreringen.                                                                                                                                                                                                                       |
| K8 | Migrerade ärenden är märkta, så att handläggare ser att ärendet är migrerat och kan söka fram alla migrerade ärenden.                                                                                                                                          |
| K9 | Migreringsappen skapar relationen mellan varje migrerat tillsynsärende och tillståndsärendet för samma serveringsställe och tillståndshavare, en gång per par, även vid omkörning (avsnitt 7.7.1).                                                             |

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

**Anmälningar.** I AlkT finns anmälningar bara som ärenden, till exempel anmälan av cateringlokal och ändring av serveringsansvariga. Anmälningar om folköl, e-cigaretter och tobaksfria nikotinprodukter finns i AlkOL (avsnitt 3.4, beslut 21).

Det som inte migreras bevaras enligt dokumenthanteringsplanen, till exempel genom leverans till e-arkiv.

## 3. Källsystem

### 3.1 AlkT

AlkT är en SQL Server 2022-databas (`AlkTSundsvall`, sorteringsordning Finnish_Swedish_CI_AS). Databasen har en äldre kompatibilitetsnivå, under 110, så frågor mot den kan inte använda funktioner som kräver en högre nivå, till exempel `TRY_CONVERT`. Databasen är 450 MB, men tabellerna använder bara cirka 65 MB; den största är loggtabellen med 50 MB. Applikationen läser databasen direkt med ett konto som bara har läsrätt. Ingen export görs.

Tabellerna som migreringen använder:

|          Tabell           |   Rader    |                                                       Innehåll                                                       |                                  Används till                                   |
|---------------------------|------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `Gällande_Tillstånd`      | 135        | Gällande tillstånd, ett per serveringsställe: drycker, omfattning, tider, perioder, utfärdandedatum och diarienummer | Kärnan i både ärendet och tillståndet                                           |
| `Objekt`                  | 354        | Serveringsställe: namn, adress, serveringsställenummer, typ, nuvarande ägare                                         | Serveringsställe och ägare                                                      |
| `Ägare`                   | 542        | Tillståndshavare: organisationsnummer, namn, bolagstyp, adress, kontaktuppgifter                                     | Tillståndshavare                                                                |
| `ObjVillkor`              | 100        | Villkor på serveringsställen, med kod, fritext och giltighetsdatum                                                   | Villkor                                                                         |
| `Ärende_Beslut`           | 2 212      | Beslut. Bara det beslut som tillståndet pekar på används.                                                            | Senaste beslut, som upplysning                                                  |
| `Klartext` och `KodTyper` | 253 och 25 | Kodlistor                                                                                                            | Översätter koder till text                                                      |
| `PBI_ägare`               | 1 164      | Personer med betydande inflytande                                                                                    | Intressenter med rollen `CONTACT` och parametern `PBI` (beslut 15, avsnitt 7.2) |

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
- **Dokumenten ligger inte i databasen.** Tabellerna hänvisar till filer med bara filnamn, utan någon mappsökväg. Enligt AlkT:s inställningar ligger dokumenten inte i databasen och inte i en mapp per serveringsställe (`DocInDB=0`, `Objektmapp=0`). För de 132 serveringsställena med tillstånd finns 10 234 filreferenser med 10 112 olika filnamn, fördelade på 1 298 ärenden (analys omgång 4):

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

### 3.4 AlkOL (fas 2)

**Namnet AlkOL (valt 2026-10-12).** Databasen heter AlkOL2 och innehåller inte bara tobakstillstånd utan också anmälningar om folköl, e-cigaretter och tobaksfria nikotinprodukter. Därför kallas källan AlkOL och inte tobaksdatabasen: `ALKOL` i statustabellen, rapporten och migreringsappens API, och "AlkOL" i det som syns i ärendena och tillstånden, till exempel parametern `migrerad` ("ÅÅÅÅ-MM-DD från AlkOL") och tillståndets `migreradFran`.

Tobakstillstånden ligger i en egen databas som vi ska få tillgång till. Den analyseras på samma sätt som AlkT:

1. tabellstruktur utan data,
2. storlek per tabell,
3. sammanräknade siffror och kodlistor.

Därefter läggs en mappning för tobak till i det här dokumentet. Migreringsappen får en andra källa, men flödet och spärrarna är desamma.

### 3.5 Filarean (fas 2)

Dokumenten ligger på en filarea som vi ska få tillgång till. Eftersom AlkT bara sparar filnamn måste vi ta reda på hur mapparna är ordnade. Det görs med en sammanställning av mappstrukturen som inte innehåller fil- eller mappnamn: antal nivåer, mappnamn där siffror och bokstäver är ersatta, antal filer, filtyper och storlek. Sammanställningen visar också hur många av de 10 112 filnamnen som finns på filarean, särskilt de 2 041 som hör till tillstånden, och hur stora de är. Därefter avgör beslut 7 vilka dokument som följer med.

Dokument som inte migreras ska ändå bevaras enligt kommunens dokumenthanteringsplan. Det gäller även ärendehistoriken i AlkT. Hur det görs, till exempel genom leverans till e-arkiv, ligger utanför migreringen men måste vara bestämt innan AlkT stängs.

## 4. Målsystem

|      System       |                                                          Vad det får                                                          |                                        Anrop                                        |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| SupportManagement | Ett avslutat, märkt ärende per tillstånd i namespace ALKT                                                                     | Söka på extern tagg, skapa ärende, aktivera ärende. I fas 2 även ladda upp bilagor. |
| PartyAssets       | Ett tillstånd per tillstånd, kopplat till ärendet                                                                             | Skapa tillstånd, söka på tillståndets id tillsammans med partyId                    |
| Party             | Ingenting. Används för att slå upp partyId.                                                                                   | Slå upp partyId från organisations- eller personnummer                              |
| licensed-business | Ingenting. Serveringsställenumret och dess nuvarande tilldelning stäms av mot registret (avsnitt 7.6).                        | Slå upp ett serveringsställenummer och dess tilldelning                             |
| Relation          | En relation mellan tillståndsärendet och varje tillsynsärende med samma serveringsställe och tillståndshavare (avsnitt 7.7.1) | Söka relationer, skapa relation                                                     |
| pw-alkt           | Ingenting. Får aldrig nås av migreringen.                                                                                     | Inga                                                                                |

## 5. Lösningsöversikt

```
AlkT (SQL Server) ─┐                                ┌─► Party              (partyId)
                   ├─► Migreringsapp ──────────────┼─► licensed-business  (serveringsställenummer)
AlkOL ─────────────┤   · källor per databas         ├─► PartyAssets        (tillstånd, länkat till ärendet)
(fas 2)            │   · regler och mappning        ├─► SupportManagement  (ärende: utkast → aktivt)
Filarea (fas 2) ───┘   · statustabell och rapport   └─► Relation           (tillståndsärende → tillsynsärende)
                                                        ✕ pw-alkt    ingen process får starta
                                                        ✕ Messaging  inga mail eller sms
```

### 5.1 Migreringsappen

- **Teknik:** tjänsten `api-service-aot-loader`, byggd på Spring Boot med dept44 8 (Java 25). Statustabell, undantagslista, uppslag av organisationsnummer och dubblettskydd byggs i tjänsten.
- **Klienter:** klienterna mot SupportManagement, PartyAssets och licensed-business genereras från samma API-specifikationer som pw-alkt använder, så fältnamn och versioner stämmer. Party-klienten är ny: pw-alkt slår bara upp person- eller organisationsnummer från partyId, inte tvärtom.
- **Källor:** en läsare per källdatabas, först AlkT och sedan AlkOL. Varje läsare gör om sina rader till en gemensam intern modell av ett tillstånd. Därefter är resten av flödet gemensamt.
- **Egen databas:** statustabell och undantagslista (avsnitt 7.1.1 och 8). Den innehåller inga namn eller nummer på personer eller bolag.
- **Körlägen:**
  - provkörning, som bara läser och skriver en rapport,
  - skarp körning, med en gräns för hur många tillstånd som körs åt gången,
  - omkörning, som fortsätter där varje tillstånd stannade.
- **En körning åt gången.** Appen startas manuellt och vägrar starta om en annan körning pågår. Det är en förutsättning för dubblettskyddet (avsnitt 6).
- **Var den körs:** på en utvecklardator (beslut 10), med VPN-åtkomst till AlkT:s SQL Server och API-gatewayen. Statustabellen och undantagslistan ligger i en lokal databas på datorn och sparas efter körningen som underlag. Säkerhetskraven står i avsnitt 12.

## 6. Flöde per tillstånd

Varje tillstånd går igenom samma steg. Varje steg kan köras om, och appen fortsätter från det steg som senast lyckades.

|        Steg         |                                                                                                                                                                       Vad som händer                                                                                                                                                                       |                                Skydd mot dubbletter                                 |                         Om det går fel                         |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------------|
| 0. Läs och bedöm    | Källraden läses och prövas mot granskningsreglerna (avsnitt 7.1.1) och undantagslistan.                                                                                                                                                                                                                                                                    | –                                                                                   | Tillstånd som ska granskas stannar här och listas i rapporten. |
| 1. Tillståndshavare | Organisations- eller personnumret görs om till partyId via Party.                                                                                                                                                                                                                                                                                          | Uppslag, ingen skrivning                                                            | Saknas i Party: tillståndet granskas (G10).                    |
| 2. Ärende           | Appen söker efter ett ärende med den externa taggen `migreringsid`, vars värde är källa, sort och käll-id, till exempel `ALKT-TILLSTAND-1234`. Finns inget skapas ett ärende som utkast, med taggen och med diarienumret i taggen `diarienummer`.                                                                                                          | Taggen `migreringsid` (beslut 4, se nedan)                                          | Felet sparas och steget görs om vid nästa körning.             |
| 3. Bilagor (fas 2)  | Filerna från det ärende som utfärdade tillståndet och serveringsställets egna filer (beslut 7) hämtas från filarean och laddas upp till utkastet. Länkar (`.url`) hoppas över. Inga andra filer hoppas över, inte heller filer från händelser som är sekretessmarkerade i AlkT (valt 2026-10-12). En fil som saknas på filarean noteras i rapporten (G14). | Varje fil laddas upp en gång per ärende; filnamn och innehåll jämförs vid omkörning | Som ovan                                                       |
| 4. Tillstånd        | Tillståndet skapas i PartyAssets med ärendets id som `assetId`, diarienumret som parameter och en länk till ärendet. Svar 409 betyder att det redan finns, och då hämtas det med tillståndets id och partyId.                                                                                                                                              | Tillståndets id (`assetId`)                                                         | Som ovan                                                       |
| 5. Aktivera         | Ärendet aktiveras. Först nu syns det för handläggarna.                                                                                                                                                                                                                                                                                                     | Ett ärende som redan är aktivt hoppas över                                          | Som ovan                                                       |
| 6. Klart            | Statustabellen får ärende-id, ärendenummer, tillståndets id (`assetId`) och PartyAssets interna id.                                                                                                                                                                                                                                                        | –                                                                                   | –                                                              |

**Den externa taggen.** En extern tagg är ett par av nyckel och värde på ärendet (fältet `externalTags`). Den kopplar ärendet till en post i ett annat system, och handläggarna ser den normalt inte. Det är inte samma sak som `externalReferences`, som bara finns på konversationer. sm-loader använder samma mekanism för att koppla ärenden till Open-E.

**Diarienumret som nyckel mot det gamla systemet (beslut 4).** Diarienumret i AlkT är nyckeln mot det gamla systemet, både på ärendet och på tillståndet. Det kräver att varje tillstånd har ett diarienummer och att inget diarienummer finns på två tillstånd. 9 tillstånd saknar diarienummer i dag; de och eventuella dubbletter granskas (G15, G16) och rättas genom registervård i AlkT.

**Migreringsid som nyckel vid omkörning (beslut 4).** Omkörningen känner igen ärendet på taggen `migreringsid`, som bygger på käll-id:t. Den finns på alla ärenden, också på anmälningar och tillsynsärenden, som saknar diarienummer eller delar det med andra (beslut 22). Dubbletter av diarienummer i källan upptäcks av G16 innan något skapas.

**Dubblettskyddet** bygger på fyra saker:
- Sökningen i steg 2 måste ange `lifecycle` (DRAFT eller ACTIVE). Annars kommer utkast inte med, och en omkörning efter ett avbrott skulle skapa ett andra utkast.
- Varken SupportManagement eller PartyAssets har en databasregel som hindrar två ärenden med samma tagg eller två tillstånd med samma id. Kontrollen görs i kod, så skyddet förutsätter att bara en körning pågår åt gången.
- Tillståndets `assetId` är ärendets id. Hittar omkörningen ärendet via taggen `migreringsid` får tillståndet samma id igen, och PartyAssets svarar 409 i stället för att skapa en dubblett.
- PartyAssets svar 409 innehåller inget id. Tillståndet hämtas därför med en sökning på tillståndets id och partyId.

**Ordningen.** Ett utkast syns inte för handläggarna och utlöser varken åtgärder, notiser eller processer. Därför skapas ärendet som utkast och aktiveras sist, när allt annat är klart. Tillståndet i PartyAssets är däremot aktivt från steg 4. Stoppas körningen mellan steg 4 och 5 finns alltså ett aktivt tillstånd kopplat till ett ärende som ännu inte syns. Omkörningen slutför det.

## 7. Mappning

### 7.1 Urval och bedömning

|       Bedömning       |                                  Villkor                                  |                                 Resultat                                 |
|-----------------------|---------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Migreras              | Ingen granskningsregel slår till, eller undantagslistan säger "migrera"   | Aktivt ärende och tillstånd med status ACTIVE                            |
| Migreras som utgånget | Undantagslistan säger "migrera som utgånget"                              | Aktivt ärende och tillstånd med status EXPIRED                           |
| Granskas              | Någon granskningsregel slår till och tillståndet saknas i undantagslistan | Inget skapas förrän det är rättat i AlkT eller avgjort i undantagslistan |
| Migreras inte         | G18 eller G25 slår till och tillståndet saknas i undantagslistan          | Inget skapas; tillståndet står i rapporten                               |

Utgångna tillstånd hanteras alltså alltid via granskningen: verksamheten avgör per tillstånd om det avslutas i AlkT, migreras som utgånget eller migreras ändå (beslut 1 och 14).

#### 7.1.1 Granskning

Vilka tillstånd som ska granskas avgörs av regler, inte av handpåläggning. Samma regler används varje gång, så listan blir densamma tills datat ändras.

| Nr  |                                                                                                                 Regel                                                                                                                 | Kontrolleras mot  |          Antal 2026-10-05          |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|------------------------------------|
| G1  | Tillståndet saknar serveringsställe                                                                                                                                                                                                   | AlkT              | 3                                  |
| G2  | Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare                                                                                                                                                                | AlkT              | 3                                  |
| G3  | Det kopplade beslutet har passerat sitt sista giltighetsdatum                                                                                                                                                                         | AlkT              | 6                                  |
| G4  | Datumperioden har passerat                                                                                                                                                                                                            | AlkT              | 2                                  |
| G5  | Gäller inte året runt men saknar period                                                                                                                                                                                               | AlkT              | 5                                  |
| G6  | Serveringsstället saknar serveringsställenummer                                                                                                                                                                                       | AlkT              | 4                                  |
| G7  | Ägarens organisationsnummer finns på flera ägarposter                                                                                                                                                                                 | AlkT              | 1 nummer                           |
| G8  | Ägarens nummer saknas eller har fel format                                                                                                                                                                                            | AlkT              | 0                                  |
| G9  | En säsong, period, tid, ett datum eller ett villkors giltighetsdatum går inte att tolka                                                                                                                                               | AlkT              | Okänt                              |
| G10 | Tillståndshavaren finns inte i Party                                                                                                                                                                                                  | Party             | Syns först vid provkörning         |
| G11 | Serveringsställenumret finns inte i registret i licensed-business, eller registrets nuvarande tilldelning gäller ett annat organisationsnummer än tillståndshavarens                                                                  | licensed-business | Syns först när registret är inläst |
| G12 | Gäller året runt men har en datumperiod                                                                                                                                                                                               | AlkT              | 6                                  |
| G13 | Tillståndet går inte att koppla till ärendet som utfärdade det. Upplysning, stoppar inte migreringen: tillståndet får inga filer automatiskt.                                                                                         | AlkT              | 22                                 |
| G14 | En fil som ska följa med finns inte på filarean, är större än 50 MB (beslut 24) eller tas inte emot av SupportManagement. Filen listas med namn i rapporten, så att verksamheten kan hitta den. Upplysning, stoppar inte migreringen. | Filarean          | Syns i fas 2                       |
| G15 | Tillståndet saknar diarienummer (beslut 4)                                                                                                                                                                                            | AlkT              | 9                                  |
| G16 | Diarienumret finns på mer än ett tillstånd (beslut 4)                                                                                                                                                                                 | AlkT              | Okänt, syns i granskningslistan    |
| G17 | Tillståndet går inte att ge någon undertyp enligt avsnitt 7.3.4                                                                                                                                                                       | AlkT              | Okänt, syns i granskningslistan    |
| G18 | Ett beslut om upphörande eller återkallelse är senare än det senaste beviljande beslutet. Tillståndet migreras inte, om inte undantagslistan säger annat.                                                                             | AlkOL             | Syns i provkörningen               |
| G19 | Sökanden i det beviljande ärendet är inte nuvarande ägare                                                                                                                                                                             | AlkOL             | Syns i provkörningen               |
| G20 | Ägarens organisationsnummer på försäljningsstället skiljer sig från ägarposten                                                                                                                                                        | AlkOL             | Syns i provkörningen               |
| G21 | Försäljningsstället saknar ägare, eller ägaren saknas                                                                                                                                                                                 | AlkOL             | Syns i provkörningen               |
| G22 | Anmälan finns bara som markering på försäljningsstället och får försäljningsställets tidigaste kända datum. Upplysning, stoppar inte migreringen.                                                                                     | AlkOL             | Syns i provkörningen               |
| G23 | Anmälan saknar datum, och försäljningsstället har inga händelser eller ärenden                                                                                                                                                        | AlkOL             | Syns i provkörningen               |
| G24 | Serveringsställenumret på tillståndet skiljer sig från serveringsställets                                                                                                                                                             | AlkT              | Okänt, syns i granskningslistan    |
| G25 | Försäljningsstället är markerat som upphört men har gällande tobakstillstånd. Tillståndet migreras inte, om inte undantagslistan säger annat.                                                                                         | AlkOL             | Syns i provkörningen               |
| G26 | Serveringsstället har flera rader i Gällande_Tillstånd, och bara en kan migreras. Undantagslistan kan inte släppa igenom det.                                                                                                         | AlkT              | Okänt, syns i granskningslistan    |
| G27 | Tillståndet pekar på ett beslut som saknas i AlkT. Upplysning, stoppar inte migreringen.                                                                                                                                              | AlkT              | Okänt, syns i granskningslistan    |
| Ä1  | Ärendet är inte avslutat i källsystemet och ska avslutas eller flyttas för hand (beslut 16 och 23)                                                                                                                                    | AlkT, AlkOL       | Syns i provkörningen               |

Ett tillstånd kan träffas av flera regler. I dag ger G1–G8, G12 och G15 drygt 30 träffar.

Organisations- och personnummer jämförs som normaliserade nummer (avsnitt 7.2). Samma nummer skrivet på olika sätt, med eller utan sekel och bindestreck, ger alltså ingen träff på G7, G19 eller G20.

**Upplysningar utan ärende.** Två sorters rader i rapporten är inga regelträffar utan upplysningar om försäljningsställen i AlkOL som inte ger något ärende och inte migreras:
- Ö1: ett aktivt försäljningsställe utan anmäld produkt och utan tobakstillstånd,
- Ö2: ett aktivt försäljningsställe som är markerat för tobak men saknar gällande tobakstillstånd.

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
| `KontaktPerson`                      | Parameter `kontaktperson`                             | Förslag. Alternativet är en egen intressent med rollen `CONTACT`; den skiljs då från personerna med betydande inflytande genom att parametern `PBI` saknas.                                                                                                                                                                                                                                                                                                              |

**Personer med betydande inflytande (beslut 15).** Varje aktiv post i `PBI_ägare` för tillståndshavaren blir en intressent i ärendet (valt 2026-10-12):

|                Källa (`PBI_ägare`)                 |      Mål på intressenten       |                                                                  Regel                                                                  |
|----------------------------------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| –                                                  | `role`                         | `CONTACT`. Rollen måste finnas i ALKT.                                                                                                  |
| –                                                  | Parameter `PBI`                | `true`. Det är parametern som visar att intressenten är en person med betydande inflytande, eftersom andra kontakter kan få samma roll. |
| `Personnr`                                         | `externalId`, `externalIdType` | partyId från Party, som PRIVATE. Personnumret skickas aldrig till SupportManagement.                                                    |
| `Namn`                                             | `firstName`                    | Hela namnet, eftersom AlkT bara har ett namnfält                                                                                        |
| `Befattningskod1`–`Befattningskod3` med kodlista P | Parameter `befattning`         | Ett värde per kod, till exempel "Verkställande direktör"                                                                                |

- Personer utan svenskt personnummer (`UtlandskPerson`) får ingen `externalId` utan bara namn.
- En person som inte finns i Party läggs till utan `externalId`, och rapporten anger hur många det gällde.
- Passiva poster (`PASSIV`) tas inte med.
- Samma person kan stå på flera ärenden om ägaren har flera tillstånd.

#### 7.2.1 Serveringsstället på ärendet

Serveringsstället blir parametrar på ärendet, under samma nycklar som pw-alkt läser för nya tillstånd, så att nya och migrerade ärenden ser likadana ut (beslut 18, ändrat 2026-10-12). Ärendet har ingen intressent för serveringsstället. Försäljningsställen i AlkOL får samma parametrar, utom serveringsställenumret, och dessutom `kategori`.

|          Källa (`Objekt`)          |       Parameter på ärendet        |                                                                                                                                                               Regel                                                                                                                                                                |
|------------------------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ServeringsNamn`                   | `premisesName`                    | Som pw-alkt                                                                                                                                                                                                                                                                                                                        |
| `ServeringsGAdress`                | `premisesStreetAddress`           | Som pw-alkt                                                                                                                                                                                                                                                                                                                        |
| `ServeringsPNr`                    | `premisesPostalCode`              | Som pw-alkt                                                                                                                                                                                                                                                                                                                        |
| `ServeringsPOrt`                   | `premisesPostalArea`              | Som pw-alkt                                                                                                                                                                                                                                                                                                                        |
| `TelefonNr1`, `Email`              | `premisesPhone`, `premisesEmail`  | Migreringens egna nycklar, namngivna som pw-alkts. Tomma värden utelämnas.                                                                                                                                                                                                                                                         |
| `RestaurangBeteckning`             | `restaurantNumber`                | Som pw-alkt. 8 siffror. Saknas numret granskas tillståndet (G6).                                                                                                                                                                                                                                                                   |
| `ObjektGrupp1` med kodlista Q      | `typAvServeringsstalle` (förslag) | Text, till exempel "Kvartersrestaurang". 13 serveringsställen saknar typ. Koderna UNG och UTOK är markeringar, inte typer, och blir parametern `markering`.                                                                                                                                                                        |
| `OmradesBeteckning` med kodlista G | `omrade`                          | Text, till exempel "City". Försäljningsställen i AlkOL får samma parameter. Ett ställe i området Timrå migreras till Timrå kommun, 2262 (beslut 19): ärendet, tillståndet, uppslagen i Party och relationen görs för den kommunen, och statustabellen sparar kommunen. Övriga områden hör till kommunen som körningen startas för. |

Parametrarna följer samma regler som ärendets övriga: nyckeln högst 255 tecken och varje värde högst 3 000 tecken.

### 7.3 Ärende i SupportManagement

|       Fält        |                                                                                                                                                                                  Värde                                                                                                                                                                                  |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `title`           | "Serveringstillstånd – {serveringsställets namn}"                                                                                                                                                                                                                                                                                                                       |
| `description`     | En läsbar sammanfattning av drycker, vem serveringen gäller, omfattning, tider och villkor                                                                                                                                                                                                                                                                              |
| `priority`        | MEDIUM                                                                                                                                                                                                                                                                                                                                                                  |
| `status`          | En avslutad status i ALKT (beslut 5). Statusen syns för kunden på Mina sidor och måste också finnas i case-status (avsnitt 7.3.5).                                                                                                                                                                                                                                      |
| `classification`  | Kategori och typ för befintliga tillstånd (beslut 5)                                                                                                                                                                                                                                                                                                                    |
| `labels`          | Samma etiketter som ett vanligt ärende för samma sorts tillstånd (avsnitt 7.3.4), även etiketter med `processKey`, plus etiketten "Migrerat ärende". Etiketten spärrar alla processer för ärendet (avsnitt 7.3.1, beslut 5 och 17).                                                                                                                                     |
| `channel`         | MIGRATION (förslag)                                                                                                                                                                                                                                                                                                                                                     |
| `reporterUserId`  | alkt-migration. Fältet är obligatoriskt och ingen person har anmält ärendet.                                                                                                                                                                                                                                                                                            |
| `assignedUserId`  | Tomt, så att ingen handläggare får en notis om ärendet. Prenumeranter får ändå interna notiser när ärendet aktiveras, se R3.                                                                                                                                                                                                                                            |
| `businessRelated` | true                                                                                                                                                                                                                                                                                                                                                                    |
| `lifecycle`       | DRAFT när ärendet skapas, ACTIVE i sista steget                                                                                                                                                                                                                                                                                                                         |
| `externalTags`    | `migreringsid` = källa, sort och käll-id, till exempel `ALKT-TILLSTAND-1234`, som omkörningen känner igen ärendet på (avsnitt 6). `diarienummer` = diarienumret i källan, nyckeln mot det gamla systemet (beslut 4), när det finns. En anmälan i AlkOL får också taggen `alkolAnmalan` = försäljningsställets id och produkten, till exempel `1234-FOLKOL` (beslut 22). |
| `stakeholders`    | Tillståndshavaren och personerna med betydande inflytande, med rollen `CONTACT` och parametern `PBI` (avsnitt 7.2). Serveringsstället är parametrar på ärendet (avsnitt 7.2.1).                                                                                                                                                                                         |
| `parameters`      | Enligt tabellen nedan                                                                                                                                                                                                                                                                                                                                                   |

#### 7.3.1 Märkning av migrerade ärenden

Migrerade ärenden märks på fem sätt. Etiketten är det handläggarna ser. De övriga fyra är tekniska och kommer med automatiskt.

|                     Märkning                     |     Syns för handläggare     | Går att filtrera på |                                      Syfte                                       |
|--------------------------------------------------|------------------------------|---------------------|----------------------------------------------------------------------------------|
| Etiketten "Migrerat ärende"                      | Ja, bland ärendets etiketter | Ja                  | Visar att ärendet är migrerat och att uppgifterna kommer från det gamla systemet |
| Parametern `migrerad` ("ÅÅÅÅ-MM-DD från AlkT")   | Ja, bland parametrarna       | Ja                  | Visar när och varifrån ärendet migrerades                                        |
| Kanal `MIGRATION`                                | Beror på gränssnittet        | Ja                  | Skiljer migrerade ärenden från inkomna                                           |
| Extern tagg `migreringsid` (källa, sort och id)  | Normalt inte                 | Ja                  | Känner igen ärendet vid omkörning och skyddar mot dubbletter (avsnitt 6)         |
| Extern tagg `diarienummer` (diarienumret i AlkT) | Normalt inte                 | Ja                  | Nyckel mot källsystemet                                                          |

Etiketten läggs som en egen etikett i ALKT, utanför trädet med tillståndstyper: under taggroten som `TAGROOT/MIGRATED_ERRAND`, med klassificeringen `TAG` som de övriga etiketterna där och attributet `processBlocked=true` (avsnitt 7.3.2). Källsystemet (AlkT eller AlkOL) syns i parametern och den externa taggen, så det räcker med en etikett för båda källorna.

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

Tomma värden utelämnas, och ett värde får vara högst 3 000 tecken. Serveringsställets namn, adress, nummer och typ ligger också bland ärendets parametrar, under pw-alkts nycklar (avsnitt 7.2.1).

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

#### 7.3.4 Etiketter per tillstånd

Ett migrerat ärende får samma etiketter som ett ärende som kommer in för samma sorts tillstånd (beslut 5). Etiketterna nedan kommer ur etikettträdet i ALKT (Bilaga B).

- Appen anger bara den djupaste etiketten. SupportManagement lägger själv till föräldrarna, så ett ärende med "Stadigvarande servering" bär också "Serveringstillstånd för alkoholdrycker" och "Alkohol".
- En etikett pekas ut med sitt id, och id:t är olika i test och produktion. Appen slår därför upp id:t för varje etikett via `resourcePath` när den startar, och startar inte om någon etikett saknas.
- Appen skickar bara etikettens id, aldrig dess `version`. Varje ändring av etikettträdet räknar upp versionen på alla etiketter med attribut, även oförändrade, och en inaktuell version avvisas med 412.
- Etiketterna under `CATEGORYROOT` har `processKey`. De migrerade ärendena bär dem ändå, eftersom "Migrerat ärende" spärrar alla processer (avsnitt 7.3.2).

**Etiketter som varje migrerat serveringstillstånd får:**

|              Etikett              |                   `resourcePath`                    |
|-----------------------------------|-----------------------------------------------------|
| Migrerat ärende                   | `TAGROOT/MIGRATED_ERRAND`                           |
| En undertyp enligt tabellen nedan | `CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/…` |

**Undertyp (förslag, godkänns av verksamheten).** Reglerna prövas i tur och ordning, och den första som stämmer avgör. `…` står för `CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION`.

| Ordning |                                           Villkor i AlkT                                           |                                                         Undertyp                                                         |                       `resourcePath`                        |                 Antal 2026-10-05                 |
|---------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|--------------------------------------------------|
| 1       | Gäller inte året runt och har en datumperiod                                                       | Tillfälligt tillstånd – allmänheten när `ServeringAllmanhet` är markerad, annars Tillfälligt tillstånd – slutet sällskap | `…/TEMPORARY_SERVING_PUBLIC`, `…/TEMPORARY_SERVING_PRIVATE` | 1 (samma tillstånd som får `validTo` i 7.4)      |
| 2       | Servering till allmänheten eller slutna sällskap (`ServeringAllmanhet`, `ServeringSlutetSallskap`) | Stadigvarande servering                                                                                                  | `…/PERMANENT_SERVING`                                       | Nästan alla: allmänheten 119, slutna sällskap 68 |
| 3       | Catering                                                                                           | Stadigvarande catering – slutna sällskap                                                                                 | `…/PERMANENT_CATERING`                                      | Högst 22                                         |
| 4       | Gårdsförsäljning                                                                                   | Gårdsförsäljning                                                                                                         | `…/FARM_SALES`                                              | Högst 1                                          |
| 5       | Provsmakning                                                                                       | Provsmakning                                                                                                             | `…/TASTING`                                                 | Högst 3                                          |
| –       | Inget av ovanstående                                                                               | Granskas (G17)                                                                                                           | –                                                           | Okänt, syns i granskningslistan                  |

Antalen gäller markeringarna var för sig, eftersom de inte är korskörda. Har ett tillstånd för enbart catering också slutna sällskap markerat i AlkT träffas det av regel 2 och inte av regel 3. Hur ett sådant tillstånd känns igen stäms av med verksamheten.

**Ett tillstånd med flera markeringar får en undertyp (förslag).** I AlkT är catering, provsmakning och gårdsförsäljning markeringar på samma tillstånd som serveringen. Förslaget ger varje tillstånd en undertyp, och övriga markeringar står i parametern `omfattning` (avsnitt 7.3.3).
- Nackdelen är att en sökning på undertypen "Stadigvarande catering" inte hittar de tillstånd där catering står bredvid serveringen. De hittas via parametern `omfattning`.
- Alternativet är att ge tillståndet alla undertyper som stämmer. Det fungerar i SupportManagement, eftersom alla undertyper har samma `processKey`. Men Mina sidor visar bara en av dem, den som råkar komma först (avsnitt 7.3.5).

Valet är en öppen fråga (avsnitt 16).

#### 7.3.5 Ärendet på Mina sidor

Tillståndshavaren ser de migrerade ärendena på Mina sidor. case-status, som visar ärendena där, hämtar ärenden i alla namespaces i SupportManagement där kunden är intressent med rollen `PRIMARY`, om inte case-status är inställd på en annan roll för namespacet. Tillståndshavaren har den rollen (avsnitt 7.2), så varje migrerat tillstånd syns i kundens ärendelista från det att ärendet aktiveras (steg 5 i avsnitt 6). Utkast syns inte, eftersom sökningen i SupportManagement utesluter dem.

| Det kunden ser |                                                                     Varifrån det kommer                                                                      |                                                                                                          Följd för migreringen                                                                                                          |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Status         | case-status översätter ärendets status i sin egen tabell `statuses`. En status som saknas där visas som "Handläggning pågår".                                | Den avslutade statusen (beslut 5) måste finnas i `statuses` med en extern status som visar att ärendet är avslutat. Annars ser kunden ett avslutat tillstånd som pågående. Raden läggs i case-status databas, inte i SupportManagement. |
| Ärendetyp      | Visningsnamnet för den kategori och typ i ALKT som matchar ärendets `classification`. Finns ingen sådan används visningsnamnet på ärendets djupaste etikett. | Utan matchande kategori och typ ser kunden undertypen, till exempel "Stadigvarande servering". "Migrerat ärende" ligger grundare i trädet och visas aldrig.                                                                             |

Tillsynsärendena syns på samma sätt om tillståndshavaren får rollen `PRIMARY` på dem (avsnitt 7.7). Rollen avgör alltså om kunden ser tillsynen.

Om de migrerade ärendena ska synas för kunden är inte beslutat (avsnitt 16). Ska de inte synas måste det lösas före produktionskörningen, utanför migreringsappen.

### 7.4 Tillstånd i PartyAssets

|          Fält          |                                                                                                                                                                                                                        Värde                                                                                                                                                                                                                        |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `assetId`              | Ärendets id, ett UUID som för nya tillstånd (beslut 4). Det gör omkörningar säkra mot dubbletter (avsnitt 6).                                                                                                                                                                                                                                                                                                                                       |
| `origin`               | SUPPORTMANAGEMENT, samma som för nya tillstånd (beslut 4). Migrerade tillstånd känns igen på parametrarna `diarienummer` och `migreradFran`.                                                                                                                                                                                                                                                                                                        |
| `partyId`              | Från Party, enligt 7.2                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `type`                 | Samma typ som pw-alkt ger tillståndet (beslut 20): `AlcoholServingPermit` för serveringstillstånd, `TobaccoSalesPermit` för tobakstillstånd, `LowAlcoholBeerSalesPermit` och `LowAlcoholBeerServingPermit` för anmälningar om försäljning och servering av folköl, och `ECigaretteSalesPermit` för anmälningar om e-cigaretter. pw-alkt har ingen typ för tobaksfria nikotinprodukter; de anmälningarna får `TobaccoFreeNicotineSalesNotification`. |
| `title`                | Samma namn som tillstånd som skapas i AoT-lösningen (beslut 4). pw-alkt tar namnet från beslutets rubrik, till exempel "Beslut om serveringstillstånd"; namnet per tillståndstyp hämtas därifrån.                                                                                                                                                                                                                                                   |
| `description`          | "Serveringstillstånd för {serveringsställets namn}"                                                                                                                                                                                                                                                                                                                                                                                                 |
| `issued`               | `Utfardande_Datum`, finns på alla 135 tillstånd                                                                                                                                                                                                                                                                                                                                                                                                     |
| `validTo`              | Sätts bara när tillståndet inte gäller året runt och har en datumperiod; då blir periodens slut slutdatum. I dag gäller det ett tillstånd som inte är granskat. Övriga datumperioder fångas av G4 och G12.                                                                                                                                                                                                                                          |
| `status`               | ACTIVE, eller EXPIRED när undantagslistan säger "migrera som utgånget"                                                                                                                                                                                                                                                                                                                                                                              |
| `statusReason`         | Utelämnas. PartyAssets har i dag inga skäl konfigurerade för ACTIVE eller EXPIRED, och en tom sträng avvisas. Det kontrolleras före körningen.                                                                                                                                                                                                                                                                                                      |
| `additionalParameters` | Se nedan                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Länk till ärendet      | Query-parametern `sourceReference` på `POST /2281/assets`, URL-kodad: `LINK\|{errandId};case;supportmanagement;ALKT\|`. Samma format som pw-alkt skickar till `/asset-drafts`. PartyAssets skriver då en relation i relationstjänsten.                                                                                                                                                                                                              |
| Header                 | `X-Sent-By: alkt-migration; type=migration`, så att migreringen syns som aktör i tillståndets historik                                                                                                                                                                                                                                                                                                                                              |

**`additionalParameters`** har samma nycklar som AoT-lösningen ger de tillstånd den skapar, så att migrerade och nya tillstånd ser likadana ut (valt 2026-10-12). Ett värde som källan inte har utelämnas, och så gör nycklar som bara en handläggare fyller i den nya lösningen: `servingAreaDrawingReference`, `executionDate`, `issuedByNameAndTitle`, `issuedByUnit`, `decisionOfficerAndCaseNumber`, `premisesPropertyDesignation`, tobakstillståndens giltighet och `newRestaurantNumber`, som bara gäller nya serveringsställen. Ja och nej skrivs `true` och `false`, en lista med komma och rader med radbrytning.

Ett värde får högst vara 255 tecken. Databaskolumnen är `varchar(255)` och API:et kontrollerar inte längden, så ett för långt värde ger ett fel vid sparandet. Ett längre värde kortas därför och avslutas med en hänvisning till ärendet, där hela texten finns.

|                                   Nyckel                                    |                                        Serveringstillstånd (AlkT)                                        |                       Tobakstillstånd (AlkOL)                        |                                    Anmälan (AlkOL)                                    |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `errandId`                                                                  | Ärendets id, som pw-alkt                                                                                 | Som serveringstillstånd                                              | Som serveringstillstånd                                                               |
| `permitHolderName`                                                          | `Ägare.Bolagsnamn`                                                                                       | `Ägare` i AlkOL                                                      | –                                                                                     |
| `permitHolderOrgNumber`                                                     | Organisations- eller personnumret med bindestreck före de fyra sista siffrorna, till exempel 556677-8899 | Som serveringstillstånd                                              | –                                                                                     |
| `permitHolderStreet`                                                        | `GAdress` och `GAdress2`                                                                                 | Som serveringstillstånd                                              | –                                                                                     |
| `permitHolderPostalAddress`                                                 | Postnummer och postort, till exempel "85230 Sundsvall"                                                   | Som serveringstillstånd                                              | –                                                                                     |
| `permitHolderPhone`                                                         | `TelefonNr1`                                                                                             | Som serveringstillstånd                                              | –                                                                                     |
| `premisesName`                                                              | `ServeringsNamn`                                                                                         | Försäljningsställets namn                                            | Försäljningsställets namn                                                             |
| `premisesStreet`                                                            | `ServeringsGAdress`                                                                                      | Försäljningsställets adress                                          | Försäljningsställets adress                                                           |
| `premisesPostalAddress`                                                     | Serveringsställets postnummer och postort                                                                | Försäljningsställets                                                 | Försäljningsställets                                                                  |
| `premisesPhone`                                                             | Serveringsställets `TelefonNr1`                                                                          | Försäljningsställets telefon                                         | Försäljningsställets telefon                                                          |
| `premisesRestaurantNumber`                                                  | `RestaurangBeteckning`, 8 siffror (beslut 18)                                                            | –                                                                    | –                                                                                     |
| `servingToPublic`, `servingToClosedCompany`                                 | `ServeringAllmanhet`, `ServeringSlutetSallskap`                                                          | –                                                                    | –                                                                                     |
| `scopeAllYear`, `scopeAnnualPeriod`, `scopeTasting`                         | `AretRunt`; säsong utan året runt; `Provsmakning`                                                        | –                                                                    | –                                                                                     |
| `scopePeriodFromTo`                                                         | Säsongen och datumperioden, en per rad                                                                   | –                                                                    | –                                                                                     |
| `beverageSpirits`, `beverageWine`, `beverageBeer`, `beverageOtherFermented` | `Spritdrycker`, `Vin`, `Starkol`, `AJADrycker`                                                           | –                                                                    | –                                                                                     |
| `servingAreaDescription`, `servingAreaSeats`, `servingAreaMaxPersons`       | `Serveringslokal`, `Sittplatser2`, `Hogstaantal`                                                         | –                                                                    | –                                                                                     |
| `servingHours`                                                              | Serveringstiderna, en per rad, "11:00–01:00"                                                             | –                                                                    | –                                                                                     |
| `conditions`                                                                | Villkoren, ett per rad, under samma nyckel som pw-alkt                                                   | –                                                                    | –                                                                                     |
| `salesRetail`, `salesWholesale`, `salesOnline`                              | –                                                                                                        | Detaljhandel, partihandel och distanshandel enligt vyn `FS_GT_Tobak` | –                                                                                     |
| `caseNumber`                                                                | `Utfardande_Diarienr`                                                                                    | Det beviljande ärendets diarienummer                                 | –                                                                                     |
| `decisionDateAndCaseNumber`                                                 | "`Utfardande_Datum`, `Utfardande_Diarienr`"                                                              | –                                                                    | –                                                                                     |
| `diarienummer`                                                              | Diarienumret i källan, som det står (beslut 4). Migreringens egen nyckel.                                | Som serveringstillstånd                                              | Som serveringstillstånd, när anmälan har ett                                          |
| `migreradFran`                                                              | AlkT                                                                                                     | AlkOL                                                                | AlkOL                                                                                 |
| `produkt`, `anmaldUppskattat`                                               | –                                                                                                        | –                                                                    | Produkten, och "true" när anmälningsdatumet är uppskattat. Migreringens egna nycklar. |

Serveringsställenumret ligger alltså under `premisesRestaurantNumber` på tillståndet, som i pw-alkt, och under `restaurantNumber` bland ärendets parametrar, som i pw-alkt (avsnitt 7.2.1).

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
| SupportManagement | Lagrar numret                 | Som parametern `restaurantNumber` på ärendet (avsnitt 7.2.1)                                                                                                                                                                  |
| PartyAssets       | Lagrar numret                 | Som `additionalParameter` på tillståndet (avsnitt 7.4)                                                                                                                                                                        |

Om avstämningen mot licensed-business görs över huvud taget avgörs i beslut 25, som är öppet. Förslaget är ingen integration, och migreringsappen har i dag ingen klient mot licensed-business, så G11 prövas inte.

Migreringen skapar inga nummer eller tilldelningar i licensed-business. Registret fylls av importen, och avvikelser rättas genom registervård i AlkT före migreringen (beslut 9). Annars skulle två vägar in i registret kunna ge dubbla tilldelningar.

**Samma lagring som nya tillstånd (beslut 18, valt 2026-10-05).** Numret lagras som parameter på intressenten för serveringsstället i SupportManagement och som parameter på tillståndet i PartyAssets. Det finns inte i pw-alkt ännu men läggs till där, och migreringen gör likadant. Rollen och nycklarna måste vara desamma i pw-alkt och migreringen, annars går nya och migrerade ärenden inte att söka fram tillsammans. På tillståndet har pw-alkt valt nyckeln `premisesRestaurantNumber`, och migreringen använder den (valt 2026-10-12). På serveringsställets intressent är förslaget fortfarande `restaurantNumber`; pw-alkt lägger i dag numret som `restaurantNumber` bland ärendets parametrar och har ingen intressent för serveringsstället.

licensed-business kontrollerar inte att numret har 8 siffror; det gör migreringen. Uppslaget i licensed-business slogs samman 2026-09-16, men om det är driftsatt i test och produktion är okänt.

### 7.7 Tillsynsärenden (beslut 1)

Beslut 1 tar med tillsynsärendena i den första migreringen. Tillsynen i AlkT är inte analyserad i detalj än. Avsnittet beskriver vad som är känt, hur ärendena ska se ut och vad som återstår innan mappningen kan bli klar.

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
- Ett avslutat ärende per tillsynsärende i AlkT. Det får etiketterna för tillsyn i ALKT enligt tabellen nedan, plus etiketten "Migrerat ärende", som spärrar processer också här (avsnitt 7.3.2).
- Intressenter: den tillståndshavare som ärendet gällde, med uppslag i Party som i 7.2. Serveringsstället och serveringsställenumret är parametrar på ärendet (avsnitt 7.2.1).
- Parametrar: ärendetyp, diarienummer, öppnat och avslutat datum, beslutet i tillsynsärendet (typ och datum) och anmärkningar.
- Diarienumret ligger i den externa taggen `diarienummer`, precis som för tillstånden (beslut 4).
- Migreringsappen skapar en relation från ärendet för serveringsställets migrerade tillstånd till tillsynsärendet, när serveringsstället har ett sådant och tillsynsärendet gällde samma tillståndshavare (avsnitt 7.7.1, K9).
- Filerna i tillsynsärendet följer med enligt samma princip som för tillstånden (beslut 7).
- Inga tillstånd skapas eller ändras i PartyAssets.

**Etiketter för tillsynsärenden (förslag, godkänns av verksamheten).** Etikettträdet i ALKT har kategorin Tillsyn med typerna Yttre tillsyn, Inre tillsyn och Åtgärder, och taggarna Alkoholtillsyn och Tobakstillsyn. Enligt pw-alkt är yttre tillsyn tillsyn på serveringsstället och inre tillsyn prövning av tillståndshavarens lämplighet. Precis som för tillstånden anger appen bara den djupaste etiketten (avsnitt 7.3.4).

|                          AlkT                          |            Etikett             |                `resourcePath`                 |
|--------------------------------------------------------|--------------------------------|-----------------------------------------------|
| Ärendetyp 07 "Tillsyn, ifrågasatt åtgärd"              | Inre tillsyn                   | `CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION` |
| Ärendetyp 08 "Tillsyn, restaurangrapport"              | Inre tillsyn                   | `CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION` |
| Ärendetyp 09 "Tillsyn, information m m"                | Inre tillsyn (valt 2026-10-13) | `CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION` |
| Tillsynsbesök, om de blir egna ärenden (punkt 2 nedan) | Yttre tillsyn                  | `CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION` |
| Alla tillsynsärenden                                   | Alkoholtillsyn                 | `TAGROOT/ALCOHOL_INSPECTION`                  |
| Alla tillsynsärenden                                   | Migrerat ärende                | `TAGROOT/MIGRATED_ERRAND`                     |

Typen Åtgärder motsvarar troligen de åtgärdsärenden som skapas ur en tillsyn i den nya handläggningen, och föreslås inte för migrerade ärenden. Inre och yttre tillsyn har `processKey` med `processStartMode=AUTOMATIC`. Spärren gäller även dem, så ingen process startar och "Starta process" avvisas för migrerade tillsynsärenden.

**Det som återstår innan mappningen är klar.**
1. Analys omgång 6: hur många tillsynsärenden och tillsynsbesök som gäller serveringsställen med och utan gällande tillstånd, vilka år de spänner över, hur ägare och diarienummer är ifyllda, och filerna per ärende.
2. Avgränsning: om alla tillsynsärenden tas med eller bara de på serveringsställen med gällande tillstånd, och om tillsynsbesöken blir egna ärenden, uppgifter på tillsynsärendet eller bara datum för senaste tillsyn på serveringsstället.
3. Att verksamheten godkänner etiketterna i tabellen ovan.
4. Vilka personer som följer med. Serveringspersonal vid besöken finns med personnummer i `Tillsyn_Servpers`. Enligt beslut 15 följer personal inte med.
5. Vilken roll tillståndshavaren får på tillsynsärendet. Med rollen `PRIMARY` syns tillsynsärendet för kunden på Mina sidor (avsnitt 7.3.5).

#### 7.7.1 Relation mellan tillsynsärende och tillståndsärende

Relationen mellan tillsynsärendet och tillståndsärendet måste skapas av migreringsappen (K9). Den kopplar ihop de två ärendena i SupportManagement, så att handläggarna kan gå från tillståndet till tillsynen på serveringsstället och tillbaka. Paren bildas via serveringsstället och tillståndshavaren (valt 2026-10-12):
- tillsynsärendet och tillståndet hör till samma serveringsställe i AlkT, och
- tillsynsärendet gällde samma tillståndshavare som det migrerade tillståndet har, alltså serveringsställets nuvarande ägare (beslut 13). Gällde tillsynsärendet en tidigare tillståndshavare skapas ingen relation.

Tillståndshavarna jämförs på organisations- eller personnummer, normaliserade enligt 7.2, och inte på ägarpost, eftersom samma nummer kan finnas på flera ägarposter (G7). Jämförelsen görs i minnet; numren sparas inte i statustabellen eller rapporten (K6).

|   Fält    |                                                                                                                  Värde                                                                                                                  |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `type`    | `LINK`, samma relationstyp som pw-alkt använder när ett ärende kopplas till ett annat ärende                                                                                                                                            |
| `source`  | Tillståndsärendet: ärendets id som `resourceId`, `type` = `case` och `namespace` = ALKT                                                                                                                                                 |
| `target`  | Tillsynsärendet, på samma sätt                                                                                                                                                                                                          |
| `service` | `supportmanagement` på både källa och mål, samma värde som pw-alkt skickar för ärenden i SupportManagement (`SupportManagementMapper.java` i pw-alkt) och som länken från tillståndet i PartyAssets har (avsnitt 7.4). Valt 2026-10-12. |

Riktningen följer pw-alkt, där det ärende som det andra utgår från är källa. Där är tillsynsärendet källa och åtgärdsärendet som skapas ur tillsynen mål. På samma sätt utgår tillsynen från tillståndet.

**Så skapas relationen.**
- Appen anropar relationstjänsten direkt (`POST /2281/relations`). Parametern `referredFrom` när tillsynsärendet skapas används inte. SupportManagement loggar bara en varning om relationen inte går att skapa den vägen, och när en omkörning hittar ett befintligt ärende skapas relationen aldrig (`ErrandService.java` rad 168–175).
- Tillståndsärendets id hämtas ur statustabellen via serveringsställets id. Tillståndet måste ha status KLAR. Därefter jämförs tillståndshavarna enligt ovan.
- Före skapandet söker appen efter en relation med samma källa och mål (`GET /2281/relations` med `filter` på källans och målets id). Finns en används dess id, annars skapas relationen. Finns flera skapas ingen ny, och avvikelsen visas i rapporten. Skyddet mot dubbletter görs alltså i kod och förutsätter, som för ärenden och tillstånd, att bara en körning pågår åt gången (avsnitt 6).
- Relationens id, från `Location`, sparas i statustabellen.
- Relationen skapas när tillsynsärendet finns som utkast och före aktiveringen. Ett aktivt tillsynsärende har därför alltid sin relation.
- Efter körningen stämmer appen av mot relationstjänsten att varje klart tillsynsärende som ska ha en relation har exakt en till tillståndsärendet, och att övriga tillsynsärenden inte har någon. Avvikelser visas i rapporten.

**När relationen inte skapas.**

|                                                       Läge                                                        |                                                               Vad som händer                                                                |
|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Serveringsstället har inget gällande tillstånd, eller tillståndet avslutas i AlkT under granskningen              | Ingen relation skapas. Tillsynsärendet migreras om avgränsningen tar med det (punkt 2 ovan), och rapporten visar att det saknar relation.   |
| Serveringsställets tillstånd är inte klart, till exempel för att det granskas eller har fått fel                  | Tillsynsärendet skapas inte. Det tas med i en senare körning, när tillståndet är klart.                                                     |
| Tillsynsärendet gällde en tidigare tillståndshavare, eller det går inte att se vilken tillståndshavare det gällde | Ingen relation skapas. Tillsynsärendet migreras ändå, och rapporten visar att det saknar relation och varför.                               |
| Relationstjänsten svarar med fel                                                                                  | Felet sparas, och steget görs om vid nästa körning, som övriga steg (avsnitt 6). Tillsynsärendet förblir ett utkast tills relationen finns. |

**Likriktning i SupportManagement (gjord 2026-10-12 på `alkt-sprint`, commit `afa2b493`).** SupportManagement skrev `support-management` i de relationer som tjänsten själv skapar:
- på det nya ärendet när ett ärende skapas med `referredFrom`,
- på båda ärendena vid överlämning mellan kommuner.

Övriga tjänster skriver tjänstens namn utan bindestreck: pw-alkt `supportmanagement`, PartyAssets `partyassets` och pw-parking-permit `casedata`. En relation som pw-alkt skapade via `referredFrom`, från tillsynen till åtgärdsärendet, fick därför `supportmanagement` på källan och `support-management` på målet.

Vad ändringen innebär:
- SupportManagement skriver nu `supportmanagement` i båda fallen. Värdet ligger i en konstant, `Constants.RELATION_SERVICE`, som delas av de två ställen som skriver relationer och av synken av konversationer.
- Synken av konversationer tar fortfarande bort bindestreck och godtar båda stavningarna (`MessageExchangeWorker.java`).
- Enhetstesterna och integrationstesterna (`ErrandsIT`, `HandoverIT`) kontrollerar nu innehållet i anropet till relationstjänsten.
- Klient-id:t `support-management` mot Notes (`ErrandNoteService.java`) är något annat och är oförändrat.
- Ändringen gäller relationer som skapas efter driftsättningen. Befintliga relationer behåller `support-management` om de inte uppdateras i relationstjänsten.
- Vi har inte kunnat kontrollera om handläggargränssnittet eller tjänster utanför de repon vi har gått igenom söker relationer på exakt `support-management`. Det stäms av med dem som äger dem innan ändringen driftsätts.

Migreringen beror inte på ändringen. Den skriver `supportmanagement` på båda ärendena oavsett.

## 8. Statustabell, rapport och spårbarhet

Statustabellen har en rad per källtillstånd. Tillsynsärendena får egna rader när mappningen i avsnitt 7.7 är klar.

|                   Kolumn                   |                                                                 Innehåll                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Källa                                      | ALKT eller ALKOL                                                                                                                          |
| Käll-id                                    | `ObjektID` i AlkT, eller `FSID` i AlkOL. För en anmälan `FSID` och produkten, till exempel `1234-FOLKOL`.                                 |
| Status                                     | NY, GRANSKAS, MIGRERAS_INTE, VANTAR, TILLSTANDSHAVARE_KLAR, ARENDE_SKAPAT, BILAGOR_KLARA, TILLSTAND_SKAPAT, RELATION_KLAR, KLAR eller FEL |
| Orsak                                      | Granskningsregel (G1–G27, Ä1) och detalj, eller vad som gick fel, utan namn eller nummer                                                  |
| Kommun                                     | Kommunen ärendet skrevs till (beslut 19). Ett ärende flyttas aldrig till en annan kommun vid en omkörning.                                |
| Ärende-id och ärendenummer                 | Från SupportManagement                                                                                                                    |
| Tillståndets id och PartyAssets interna id | `assetId` och id:t ur `Location`. Det interna id:t behövs för att hämta, ändra eller radera tillståndet.                                  |
| Relationens id                             | Bara för tillsynsärenden: relationen till tillståndsärendet (avsnitt 7.7.1). Behövs för avstämning och återställning.                     |
| Körning och tidpunkter                     | För spårbarhet                                                                                                                            |

Rapporten efter varje körning visar antal per status och en lista med en rad per tillstånd:
- käll-id,
- serveringsställenummer och serveringsställets namn,
- status och, för tillstånd som granskas, regel och detalj.

Serveringsställets nummer och namn finns med för att verksamheten ska kunna hitta tillståndet i AlkT; käll-id syns inte i AlkT:s gränssnitt. Rapporten innehåller aldrig tillståndshavarens namn, organisationsnummer eller personnummer. En enskild firmas organisationsnummer är ett personnummer.

Filer som inte laddas upp (G14) listas med filnamn, eftersom verksamheten behöver namnen för att hitta filerna. Ett filnamn är fritext och kan innehålla en persons namn; det är ett medvetet undantag från K6 (valt 2026-10-12). Siffror som ser ut som person- eller organisationsnummer maskeras, också i filnamnen.

Stoppas ett ärende som en tidigare körning redan har börjat skriva, till exempel för att källan har ändrats så att en granskningsregel slår till, står det i orsaken och rapporten att utkastet finns kvar i SupportManagement. Det slutförs om ärendet senare får migreras, och hanteras annars enligt avsnitt 10.3.

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

PartyAssets skickar varken händelser eller meddelanden. Länken till ärendet och relationerna mellan tillstånds- och tillsynsärenden skrivs till relationstjänsten; att den inte skickar något har vi inte kunnat kontrollera i kod.

**Kontroller före körning, i varje miljö:**
- inga aktiva automatiska åtgärder för ALKT,
- inga åtgärder som reagerar på ändringar och har villkor som matchar migrerade ärenden,
- inga prenumerationer på hela namespacet ALKT, eller ett beslut att interna notiser accepteras,
- etiketten "Migrerat ärende" finns och har attributet som spärrar processer,
- ett testärende med etiketten skriver ingen processhändelse när det ändras eller när "Starta process" används,
- statusen, rollerna och klassificeringen finns i ALKT,
- etiketterna i avsnitt 7.3.4 och 7.7 finns, och appen hittar dem via `resourcePath`,
- den avslutade statusen finns i case-status tabell `statuses` med en extern status som visar att ärendet är avslutat (avsnitt 7.3.5),
- PartyAssets kräver inget skäl för statusarna ACTIVE och EXPIRED,
- relationstypen `LINK` finns i relationstjänsten.

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

- Etiketter, status, roller (även `CONTACT`) och klassificering finns i ALKT i test och produktion, även etiketterna för tillsynsärenden (avsnitt 7.7).
- Den avslutade statusen finns i case-status i test och produktion, och det är beslutat om de migrerade ärendena ska synas på Mina sidor (avsnitt 7.3.5).
- pw-alkt och migreringen använder samma nycklar för serveringsstället och serveringsställenumret (beslut 18, avsnitt 7.2.1 och 7.4).
- Mappningen av tillsynsärendena är klar (avsnitt 7.7).
- Klientnycklar finns för SupportManagement, PartyAssets, Party, licensed-business och relationstjänsten i test och produktion.
- Ett konto med bara läsrätt finns till AlkT, och senare till AlkOL.
- Utvecklardatorn har VPN-åtkomst till AlkT:s SQL Server och till API-gatewayen i test och produktion (beslut 10).
- Serveringsställenumren är inlästa i licensed-business, och uppslaget är driftsatt.
- Spärren mot processer i SupportManagement (beslut 17) är driftsatt i test och produktion.

### 10.2 Ordning

1. Provkörning mot testmiljön. Den ger granskningslistan.
2. Verksamheten går igenom listan, gör registervård i AlkT och för in undantag (listan *Att hantera före migrering*). Provkörningen upprepas tills alla tillstånd på listan är hanterade. 135 tillstånd är få nog för att verksamheten kan gå igenom hela rapporten.
3. Skarp körning i testmiljön, följd av kontrollerna i avsnitt 9 och stickprov i handläggarnas gränssnitt.
4. Analyserna av AlkT körs om, eftersom flera regler beror på dagens datum.
5. Alla pågående ärenden i AlkT avslutas eller flyttas för hand (beslut 16).
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
- Relationer mellan tillstånds- och tillsynsärenden raderas i relationstjänsten med relationens id i statustabellen.
- Statustabellens rad återställs, och tillståndet körs om.

## 11. Test och kvalitetssäkring

- **Enhetstester för reglerna:** granskningsreglerna G1–G27 och Ä1, tolkning av säsonger, perioder, tider och nummer, uppbyggnad av parametrar, beskrivning och tillståndets parametrar med längdgränsen 255 tecken.
- **Integrationstester:**
  - målsystemen ersätts med WireMock,
  - källan är en SQL Server i Testcontainers med AlkT:s tabellstruktur, samma kompatibilitetsnivå och påhittade rader, aldrig riktiga data.
- **Testerna kräver att:**
  - varje anrop till SupportManagement har `X-Trigger-Process: false` och rätt `X-Sent-By`,
  - sökningen efter befintligt ärende anger `lifecycle`,
  - inga anrop görs till kommunikation, konversationer eller beslut,
  - en omkörning efter ett avbrott i varje steg inte skapar dubbletter.
- **Tester för relationen mellan tillsynsärende och tillståndsärende (K9, avsnitt 7.7.1).** Både integrationstester och tillhörande enhetstester skapas.
  - Enhetstester: relationens innehåll (typ, källa, mål, `service` = `supportmanagement` och namespace), uppslaget av tillståndsärendet i statustabellen, jämförelsen av tillståndshavare (samma nummer på flera ägarposter, nummer skrivna i olika format, saknad ägare), valet mellan att skapa, återanvända, vänta och hoppa över, och felhanteringen.
  - Integrationstester, med relationstjänsten i WireMock:
    - relationen skapas med rätt innehåll, och dess id sparas i statustabellen,
    - ett tillsynsärende som gällde en tidigare tillståndshavare migreras men får ingen relation,
    - en omkörning skapar ingen andra relation, inte heller efter ett avbrott mellan att relationen skapades och att statustabellen uppdaterades,
    - finns flera relationer för samma par skapas ingen ny, och avvikelsen syns i rapporten,
    - ett tillsynsärende på ett serveringsställe utan gällande tillstånd får ingen relation,
    - ett tillsynsärende vars tillstånd inte är klart skapas inte, och tas med i nästa körning,
    - ett fel från relationstjänsten lämnar tillsynsärendet som utkast, och steget görs om,
    - tillsynsärendet skapas utan `referredFrom`.
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
- Analysen delas bara som tabellstruktur och sammanräknade siffror.
- `X-Sent-By` skickas till både SupportManagement och PartyAssets, så att migreringen syns som aktör i båda systemens historik.

## 13. Fas 2: tobakstillstånd och dokument

**Tobak.** När åtkomsten finns analyseras databasen enligt 3.4. Mappningen läggs till som ett nytt avsnitt 7.8, och hur tobakstillstånden ska se ut i PartyAssets avgörs (beslut 3). pw-alkt skapar inga tobakstillstånd, så det finns ingen förlaga.

**Etiketter i fas 2.** Etiketterna finns redan i etikettträdet i ALKT (Bilaga B). Vilka anmälningar som ska med avgörs när AlkOL är analyserad (avsnitt 16).

|            Tillstånd eller anmälan             |                                  Etikett                                  |                         `resourcePath`                          |
|------------------------------------------------|---------------------------------------------------------------------------|-----------------------------------------------------------------|
| Tobakstillstånd                                | Ansökan om försäljning av tobaksvaror                                     | `CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION`                 |
| Anmälan om e-cigaretter                        | Anmälan om försäljning av elektroniska cigaretter och påfyllnadsbehållare | `CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION`            |
| Anmälan om tobaksfria nikotinprodukter         | Anmälan om försäljning av tobaksfria nikotinprodukter                     | `CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION` |
| Anmälan om försäljning av folköl               | Anmälan om försäljning av folköl                                          | `CATEGORYROOT/ALCOHOL/FOLKOL_SALES_NOTIFICATION`                |
| Anmälan om servering av folköl                 | Anmälan om servering av endast folköl (klass 2)                           | `CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION`              |
| Anmälan om försäljning och servering av folköl | Anmälan om försäljning och servering av folköl                            | `CATEGORYROOT/ALCOHOL/FOLKOL_SALES_SERVING_NOTIFICATION`        |
| Tillsyn av tobak, om den ingår                 | Tobakstillsyn                                                             | `TAGROOT/TOBACCO_INSPECTION`                                    |
| Tillsyn av tobak, ärendetyperna IT och Tills   | Inre tillsyn (valt 2026-10-13)                                            | `CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION`                   |

Alla migrerade ärenden i fas 2 får också etiketten "Migrerat ärende".

**Dokument.** Enligt beslut 7 följer filerna i det ärende som utfärdade tillståndet med, plus serveringsställets egna filer:
- **Urval.** Tillståndet kopplas till sitt utfärdandeärende via beslutet det pekar på eller via utfärdandediarienumret. 113 av 135 tillstånd får en koppling, och 18 av dem kopplas till mer än ett ärende; då följer filerna från alla de ärendena med. De 22 utan koppling listas (G13), så att verksamheten kan leta fram dokument för hand om de behövs.
- **Omfång.** Cirka 2 060 filer: cirka 980 bilagor (mest PDF, 87 Outlook-mejl), cirka 1 050 dokument som AlkT har skapat (RTF) och 13 filer på serveringsställena. Ritningarna på tillstånden saknar filändelse och är troligen beteckningar, inte filer. Det kontrolleras på filarean.
- **Matchning.** AlkT sparar bara filnamn. När filarean är kartlagd enligt 3.5 hämtas filerna på namn; namnen är nästan unika (10 234 referenser på 10 112 namn i hela databasen). En fil som inte hittas noteras i rapporten (G14).
- **Var filerna läggs.** Alla filer läggs på ärendet. Beslutsdokumenten (68 AlkT-dokument och 38 bilagor) läggs också på tillståndet i PartyAssets, eftersom pw-alkt lägger beslutets bilagor på tillstånden.
- **Att tänka på:**
- Uppladdning till ett aktivt ärende är säker enligt R1–R3, men ger en händelse i ärendets historik.
- Uppladdning till ett aktivt tillstånd ger en ny version av tillståndet.
- Tillstånd med status EXPIRED tar inte emot bilagor.
- Varje fil får vara högst 50 MB i SupportManagement.

Om dokumenten ska följa med från början bör produktionskörningen vänta på fas 2 (beslut 12).

## 14. Beslut

Beslut 1–18 togs 5 oktober 2026, beslut 19 och 21–24 togs 6 oktober 2026, och beslut 20 togs 12 oktober 2026. Beslut 25 är öppet. Beslut 3 ändrades 6 oktober 2026: typen i PartyAssets blir inte PERMIT. Alla beslut står också på sidan "Migrering av alkohol- och tobakstillstånd". Fas 1 är de 135 serveringstillstånden och tillsynsärendena i AlkT; fas 2 är tobakstillstånden, anmälningarna och dokumenten.

|                                                                                          Beslut                                                                                          |                                                                                                                                        Valt beslut                                                                                                                                        | Beskrivs i  |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| **1. Omfattning: vad migreras?**                                                                                                                                                         | Tillstånd och tillsynsärenden, från början. Gällande tillstånd och anmälningar med handlingarna från ärendet där tillståndet beviljades, samt tillsynsärendena.                                                                                                                           | 2.3, 7.7    |
| **2. Beslut i SupportManagement** Om de migrerade ärendena får ett formellt beslut i det nya systemet.                                                                                   | Nej, inga beslut skapas. Beslutsuppgifterna sparas på ärendet och i tillståndet.                                                                                                                                                                                                          | 7.3.3, 7.4  |
| **3. Tillstånden i PartyAssets** Hur de migrerade tillstånden ser ut i registret som syns på Mina sidor.                                                                                 | Som tillstånd som beviljas i det nya systemet, med samma nycklar som pw-alkt. Typen blir inte PERMIT utan avgörs i beslut 20 (ändrat 6 oktober 2026). Tobakstillstånden bestäms när AlkOL är analyserad.                                                                                  | 7.4         |
| **4. Tillståndets id och ursprung**                                                                                                                                                      | Migrerade tillstånd får samma namn som de som skapas i AoT-lösningen. Diarienumret lagras som parameter i PartyAssets och på ärendet och är nyckeln mot det gamla systemet. En omkörning känner igen ärendet på taggen `migreringsid`, så att inga dubbletter skapas (ändrat 2026-10-12). | 6, 7.3, 7.4 |
| **5. Etiketter, status och klassificering**                                                                                                                                              | Samma etiketter som vanliga ärenden, plus etiketten "Migrerat ärende", och en avslutad status.                                                                                                                                                                                            | 7.3, 7.3.1  |
| **6. Ursprungliga datum och diarienummer**                                                                                                                                               | Som uppgifter på ärendet. Diarienumret hanteras enligt beslut 4.                                                                                                                                                                                                                          | 7.3.3       |
| **7. Bilagor**                                                                                                                                                                           | Handlingarna från ärendet där tillståndet beviljades, cirka 2 060 filer. Tillstånd utan koppling till filer loggas.                                                                                                                                                                       | 3.2, 13     |
| **8. Tillståndshavare som saknas i Party**                                                                                                                                               | Tillståndet loggas, och verksamheten utreder om det inträffar.                                                                                                                                                                                                                            | 7.1.1 (G10) |
| **9. Serveringsställenummer som saknas eller inte stämmer**                                                                                                                              | Registervård i AlkT före migreringen.                                                                                                                                                                                                                                                     | 7.6         |
| **10. Var körningen sker**                                                                                                                                                               | Migreringen körs på en utvecklardator.                                                                                                                                                                                                                                                    | 5.1, 12     |
| **11. När AlkT låses för ändringar**                                                                                                                                                     | AlkT låses inte. Ändringar i AlkT efter migreringen följer inte med.                                                                                                                                                                                                                      | 10.2        |
| **12. När migreringen körs i produktion**                                                                                                                                                | Före driftstarten av den nya handläggningen. Ska dokumenten följa med från början väntar körningen tills fas 2 är klar.                                                                                                                                                                   | 10.2, 13    |
| **13. Tillståndshavare när ägaren skiljer sig**                                                                                                                                          | Serveringsställets nuvarande ägare. De 3 avvikelserna granskas.                                                                                                                                                                                                                           | 7.2         |
| **14. Perioder och tidsbegränsade tillstånd**                                                                                                                                            | Säsonger sparas som uppgift på ärendet. Slutdatum sätts bara när tillståndet inte gäller året runt och har en datumperiod. Passerade perioder granskas.                                                                                                                                   | 7.3.3, 7.4  |
| **15. Personer med betydande inflytande och personal**                                                                                                                                   | Personer med betydande inflytande följer med som intressenter på ärendet. Personalen följer inte med.                                                                                                                                                                                     | 7.2         |
| **16. Pågående ärenden i AlkT**                                                                                                                                                          | Alla 64 öppna ärenden avslutas i AlkT före produktionskörningen eller flyttas för hand.                                                                                                                                                                                                   | 10.2        |
| **17. Hur processer spärras**                                                                                                                                                            | Spärretikett. Inga processer kan startas på migrerade ärenden.                                                                                                                                                                                                                            | 7.3.2       |
| **18. Var serveringsställenumret lagras**                                                                                                                                                | Som parameter på ärendet (`restaurantNumber`) och på tillståndet (`premisesRestaurantNumber`), som pw-alkt (ändrat 2026-10-12).                                                                                                                                                           | 7.2.1, 7.6  |
| **19. Tillstånd och anmälningar i Timrå** Båda databaserna har tillstånd i området Timrå: 14 serveringstillstånd, 11 tobakstillstånd och 31 anmälningar.                                 | Samma kommun som AoT-lösningen använder för nya ärenden från Timrå, alltså Timrå kommun, 2262. Området sparas som uppgift på serveringsstället eller försäljningsstället.                                                                                                                 | 7.2.1       |
| **20. Typ i PartyAssets** Vilken typ tillstånden och anmälningarna får i PartyAssets, för både alkohol och tobak. Fortsätter beslut 3.                                                   | Samma typer som pw-alkt använder, för både alkohol och tobak (valt 2026-10-12). pw-alkt har ingen typ för tobaksfria nikotinprodukter, så de anmälningarna får `TobaccoFreeNicotineSalesNotification` (valt 2026-10-13).                                                                  | 7.4         |
| **21. Hur anmälningar migreras** AlkOL har 192 anmälningar om folköl, e-cigaretter och tobaksfria nikotinprodukter. De har inga beslut.                                                  | Ett avslutat ärende per försäljningsställe och produkt, med filerna från anmälningsärendet, och en anmälan i PartyAssets.                                                                                                                                                                 | 13          |
| **22. Nyckel för anmälningar** Hur migreringen känner igen en anmälan vid en omkörning. 63 anmälningar saknar diarienummer, och samma diarienummer används för flera butiker i en kedja. | Försäljningsställets id och produkten, sparade som extern tagg på ärendet. Tobakstillstånden känns igen som i beslut 4.                                                                                                                                                                   | 7.3, 8      |
| **23. Pågående ärenden i AlkOL** 47 ärenden är inte avslutade, 42 av dem på aktiva försäljningsställen.                                                                                  | Som beslut 16: alla 47 avslutas före produktionskörningen eller flyttas för hand. Listan *Att hantera före migrering* för tobak skickas till verksamheten.                                                                                                                                | 10.2        |
| **24. Filer större än 50 MB** 24 filer på filarean är större än gränsen i SupportManagement och PartyAssets, nästan alla RTF-dokument som systemen har skapat.                           | Filen laddas inte upp utan listas i rapporten, och handläggaren lägger till den för hand om den behövs.                                                                                                                                                                                   | 7.1.1 (G14) |
| **25. Avstämning mot licensed-business** Om migreringen ska stämma av serveringsställenumret mot registret i licensed-business, eller bara ta numret från AlkT.                          | Öppet. Förslaget är ingen integration: numret tas från AlkT och stäms av mot numret på tillståndet, och verksamheten jämför AlkT med Excel-registret före migreringen.                                                                                                                    | 7.6 (G11)   |

## 15. Risker

|                                                      Risk                                                      |                                                                 Följd                                                                  |                                                                          Åtgärd                                                                           |
|----------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spärren i SupportManagement är inte driftsatt när ärendena migreras                                            | Första ändringen av ett migrerat ärende startar en process                                                                             | Spärren är en förutsättning för körningen (10.1) och kontrolleras med ett testärende (avsnitt 9)                                                          |
| Etiketten "Migrerat ärende" saknar spärrattributet i någon miljö                                               | Som ovan                                                                                                                               | Kontroll före körning (avsnitt 9)                                                                                                                         |
| Automatiska åtgärder är inte avstängda                                                                         | Mail skickas vid körningen                                                                                                             | Kontroll före körning (R6)                                                                                                                                |
| En åtgärd som reagerar på ändringar matchar migrerade ärenden                                                  | Mail skickas första gången en handläggare ändrar ett migrerat ärende                                                                   | Kontroll före körning (R6)                                                                                                                                |
| Prenumerationer på hela namespacet finns                                                                       | Interna notiser för varje migrerat ärende                                                                                              | Kontroll före körning (R3)                                                                                                                                |
| Bolag saknas i Party, till exempel avregistrerade bolag                                                        | Tillståndet kan inte skapas                                                                                                            | Granskning (G10, beslut 8)                                                                                                                                |
| Ett värde i tillståndets parametrar är längre än 255 tecken                                                    | Tillståndet kan inte sparas                                                                                                            | Korta värden och test av längdgränsen (avsnitt 7.4)                                                                                                       |
| Åtkomsten till AlkOL eller filarean dröjer                                                                     | Fas 2 försenas                                                                                                                         | Faserna är oberoende av varandra                                                                                                                          |
| Tillståndet visar omfattningen från ett tillägg som har upphört                                                | Fel uppgifter i det migrerade tillståndet                                                                                              | Granskning (G3, G12)                                                                                                                                      |
| Perioder och beslut passerar innan produktionskörningen                                                        | Granskningslistan växer                                                                                                                | Analyserna körs om före produktion (10.2)                                                                                                                 |
| AlkT ändras under eller efter körningen                                                                        | Ändringen följer inte med till de migrerade ärendena                                                                                   | Accepterat (beslut 11). Körningen tar kort tid och görs utanför kontorstid, och verksamheten informeras om att AlkT inte längre är facit efter körningen. |
| Produktionsnycklar och data hanteras på en utvecklardator                                                      | Nycklarna kan komma på avvägar om datorn tappas bort eller nycklarna sparas i en fil                                                   | Säkerhetskraven i avsnitt 12, och nycklarna byts eller spärras efter godkänd migrering                                                                    |
| Diarienummer saknas eller finns på flera tillstånd                                                             | Tillstånden kan inte kopplas till det gamla systemet                                                                                   | Granskning (G15, G16) och registervård i AlkT                                                                                                             |
| Tillsynsärendena är inte analyserade                                                                           | Omfång och mappning kan bli större än väntat                                                                                           | Analys omgång 6 och mappning innan bygget av den delen (avsnitt 7.7)                                                                                      |
| Migrerade och nya tillstånd ser olika ut                                                                       | Mina sidor och handläggarstödet visar dem olika                                                                                        | Samordning med pw-alkt (beslut 3)                                                                                                                         |
| Den avslutade statusen saknas i case-status                                                                    | Kunden ser ett avslutat tillstånd som "Handläggning pågår" på Mina sidor                                                               | Kontroll före körning (avsnitt 9, 7.3.5)                                                                                                                  |
| Likriktningen i SupportManagement är inte driftsatt, eller befintliga relationer har kvar `support-management` | Relationer till migrerade och nya ärenden har olika `service`-värde, och en tjänst som söker på exakt värde hittar bara den ena sorten | Likriktningen är gjord på `alkt-sprint` och driftsätts med den (avsnitt 7.7.1, 16). SupportManagements egen läsning godtar båda stavningarna.             |
| Uppslaget av serveringsställenummer är inte driftsatt                                                          | Avstämningen kan inte göras                                                                                                            | Beroende i planeringen                                                                                                                                    |
| Filer saknas på filarean eller är mycket stora                                                                 | Ärenden blir utan dokument, eller uppladdningen misslyckas                                                                             | Kartläggningen av filarean i 3.5 visar det i förväg, och saknade filer listas (G14)                                                                       |
| Historiken bevaras inte när AlkT stängs                                                                        | Uppgifter som ska arkiveras går förlorade                                                                                              | Arkivleveransen bestäms innan AlkT stängs (avsnitt 3.5)                                                                                                   |
| Skapat-datum blir migreringsdagen                                                                              | Handläggare kan tro att ärendet är nytt                                                                                                | Etiketten "Migrerat ärende", parametrarna `utfardat` och `migrerad`, och information till handläggarna                                                    |

## 16. Beroenden och öppna frågor

- Åtkomst till AlkOL och filarean.
- Formatet på personnummer vid uppslag av PRIVATE i Party.
- Metadata i ALKT: status, etiketter, roller (även `CONTACT` för personer med betydande inflytande) och klassificering.
- Om det finns prenumerationer på hela namespacet ALKT.
- Om uppslaget av serveringsställenummer i licensed-business är driftsatt.
- Om `typAvServeringsstalle`, `markering` och `omrade` ska ha andra nycklar, när pw-alkt får motsvarande uppgifter.
- Vad markeringen `ALP` i AlkT betyder.
- Om säsongerna på tillstånd som gäller året runt avser uteserveringen.
- Hur AlkT:s ärendehistorik och de dokument som inte migreras bevaras enligt dokumenthanteringsplanen, till exempel genom leverans till e-arkiv.
- Vilka anmälningar som räknas som gällande och var de finns. AlkT har bara ärenden om anmälningar; AlkOL har anmälningar om folköl, e-cigaretter och tobaksfria nikotinprodukter.
- Formatet på villkorens giltighetsdatum, och serveringstiderna i intervall 2–8.
- Tillsynsärendena: omfång, avgränsning, personer, tillståndshavarens roll och godkännande av de föreslagna etiketterna (avsnitt 7.7).
- Om ett tillstånd med flera markeringar får en undertyp eller flera, och att verksamheten godkänner reglerna för undertyp (avsnitt 7.3.4).
- Om de migrerade ärendena ska synas för tillståndshavaren på Mina sidor (avsnitt 7.3.5).
- Likriktningen av `service`-värdet till `supportmanagement` i SupportManagement (gjord på `alkt-sprint`): om befintliga relationer med `support-management` ska uppdateras i relationstjänsten, och om handläggargränssnittet eller andra tjänster söker relationer på exakt `support-management` (avsnitt 7.7.1).
- Vilket namn tillstånden får per tillståndstyp i AoT-lösningen, så att migrerade tillstånd får samma (beslut 4).

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

**Befattningar för personer med betydande inflytande (kodlista P):** Bolagsman, Firmatecknare, Ledamot, Ordförande, Restaurangchef, Revisor, Suppleant, Verkställande direktör, Ägare.

## Bilaga B. Underlag

|                          Underlag                          |                                                        Var                                                         |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Tabellstruktur, storlekar och analysresultat för AlkT      | Förvaras utanför repot                                                                                             |
| Processintegrationen i SupportManagement                   | `api-service-support-management/docs/alkt-processintegration.md`                                                   |
| Hur pw-alkt bygger tillstånd i PartyAssets                 | `pw-alkt` (origin/main), `PartyAssetsMapper.java`                                                                  |
| Uppslag av partyId och skapande av tillstånd i PartyAssets | `api-service-permit-loader`                                                                                        |
| Att hitta befintligt ärende via extern tagg                | `api-service-sm-loader`, `SupportManagementService.java`. Sökningen där saknar `lifecycle` och hittar inte utkast. |
| Import av serveringsställenummer                           | `api-service-licensed-business`, `ImportService.java`                                                              |
| Etikettträdet i ALKT                                       | `GET /2281/ALKT/metadata/labels`, hämtat 2026-10-12                                                                |
| Ärenden på Mina sidor                                      | `api-service-case-status`, `SupportManagementService.java` och `StatusVocabulary.java`                             |
| Inre och yttre tillsyn                                     | `pw-alkt` (origin/main), `README.md`                                                                               |

