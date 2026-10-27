-- The tables of AlkT that the migration reads, with only the columns it reads, and made up rows. No row holds real data.
CREATE TABLE dbo.[Gällande_Tillstånd] (
    ObjektID int NULL, GTAgarID int NULL,
    Starkol bit NOT NULL DEFAULT 0, Vin bit NOT NULL DEFAULT 0, Spritdrycker bit NOT NULL DEFAULT 0, AJADrycker bit NOT NULL DEFAULT 0, ALP bit NOT NULL DEFAULT 0,
    AretRunt bit NOT NULL DEFAULT 0, ArligPeriodStart varchar(10) NULL, ArligPeriodSlut varchar(10) NULL, PeriodStart varchar(10) NULL, PeriodSlut varchar(10) NULL,
    Pausservering bit NOT NULL DEFAULT 0, Uteservering bit NOT NULL DEFAULT 0, Trafikservering bit NOT NULL DEFAULT 0, Minibar bit NOT NULL DEFAULT 0,
    Roomservice bit NOT NULL DEFAULT 0, Catering bit NOT NULL DEFAULT 0, Provsmakning bit NULL, Kryddning bit NULL, Gardsforsaljning bit NOT NULL DEFAULT 0,
    ServeringAllmanhet bit NOT NULL DEFAULT 0, ServeringSlutetSallskap bit NOT NULL DEFAULT 0,
    ServeringStartTid1 varchar(10) NULL, ServeringSlutTid1 varchar(10) NULL, ServeringStartTid2 varchar(10) NULL, ServeringSlutTid2 varchar(10) NULL,
    ServeringStartTid3 varchar(10) NULL, ServeringSlutTid3 varchar(10) NULL, ServeringStartTid4 varchar(10) NULL, ServeringSlutTid4 varchar(10) NULL,
    ServeringStartTid5 varchar(10) NULL, ServeringSlutTid5 varchar(10) NULL, ServeringStartTid6 varchar(10) NULL, ServeringSlutTid6 varchar(10) NULL,
    ServeringStartTid7 varchar(10) NULL, ServeringSlutTid7 varchar(10) NULL, ServeringStartTid8 varchar(10) NULL, ServeringSlutTid8 varchar(10) NULL,
    Hogstaantal varchar(10) NULL, Sittplatser2 varchar(10) NULL, Serveringslokal varchar(50) NULL,
    Noteringar varchar(500) NULL, Noteringar2 varchar(500) NULL, Noteringar3 varchar(500) NULL, Noteringar4 varchar(500) NULL, Noteringar5 varchar(3000) NULL,
    Noteringar5B varchar(500) NULL, Noteringar6 varchar(500) NULL, Noteringar7 varchar(500) NULL, Noteringar8 varchar(500) NULL,
    Ersatter_Datum datetime NULL, Ersatter_Diarienr varchar(20) NULL, Utfardande_Datum datetime NULL, Utfardande_Diarienr varchar(20) NULL,
    GTRestaurangBeteckning varchar(20) NULL, BeslutsID int NULL
)
GO
CREATE TABLE dbo.Objekt (
    ObjektID int NOT NULL PRIMARY KEY, AgarID int NULL, RestaurangBeteckning varchar(20) NULL, ObjektGrupp1 varchar(6) NULL, ServeringsNamn varchar(40) NULL,
    ServeringsGAdress varchar(40) NULL, ServeringsPNr varchar(6) NULL, ServeringsPOrt varchar(25) NULL, TelefonNr1 varchar(15) NULL, Email varchar(40) NULL,
    OmradesBeteckning varchar(5) NULL, ObjBifogadfil varchar(100) NULL, ObjBifogadfil2 varchar(100) NULL
)
GO
CREATE TABLE dbo.[Ägare] (
    AgarID int NOT NULL PRIMARY KEY, OrganisationsNr varchar(12) NULL, Bolagsnamn varchar(40) NULL, KontaktPerson varchar(35) NULL, GAdress varchar(40) NULL,
    GAdress2 varchar(50) NULL, PNr varchar(6) NULL, POrt varchar(25) NULL, TelefonNr1 varchar(15) NULL, EMail varchar(50) NULL
)
GO
CREATE TABLE dbo.ObjVillkor (ID int IDENTITY(1, 1) NOT NULL, Notering varchar(2000) NULL, ObjID int NULL, Villkor varchar(10) NULL, FR_OM varchar(40) NULL, T_OM varchar(40) NULL)
GO
CREATE TABLE dbo.[Ärende_Beslut] (
    BeslutsID int NOT NULL PRIMARY KEY, ArendeID int NULL, BeslutsTyp varchar(5) NULL, BeslutsDatumTid datetime NULL, GiltigtTOM_Datum datetime NULL,
    Worddokument varchar(12) NULL, BifogadFil varchar(255) NULL, Dokument varchar(5) NULL
)
GO
CREATE TABLE dbo.[Ärende] (
    ArendeID int NOT NULL PRIMARY KEY, ObjektID int NULL, ArendeTyp varchar(6) NULL, DiarieNr varchar(30) NULL, OppnandeDatum datetime NULL,
    AvslutsDatum datetime NULL, SokandeID int NULL
)
GO
CREATE TABLE dbo.[Ärende_Händelser] (HandelseID int NOT NULL PRIMARY KEY, ArendeID int NULL, Worddokument varchar(12) NULL, Bifogadfil varchar(200) NULL, ObjektID int NULL, Dokument varchar(5) NULL, HandelseDatumTid datetime NULL, HandelseTyp varchar(5) NULL)
GO
CREATE TABLE dbo.[Ärende_Händelser_Filer] (ID int IDENTITY(1, 1) NOT NULL, HandelseID int NULL, Bifogadfil varchar(255) NULL)
GO
CREATE TABLE dbo.[Ärende_Bilder] (BildID int IDENTITY(1, 1) NOT NULL, ArendeID int NULL, BildDatumTid datetime NULL, Bildfilnamn varchar(100) NULL)
GO
CREATE TABLE dbo.[PBI_ägare] (
    PBIAGID int IDENTITY(1, 1) NOT NULL, AgarID int NULL, Namn varchar(50) NULL, Personnr varchar(12) NULL, Befattningskod1 varchar(6) NULL,
    Befattningskod2 varchar(6) NULL, Befattningskod3 varchar(6) NULL, PASSIV bit NULL, UtlandskPerson bit NOT NULL DEFAULT 0
)
GO
CREATE TABLE dbo.[Sökande] (
    SokandeID int NOT NULL PRIMARY KEY, OrganisationsNr varchar(12) NULL, Bolagsnamn varchar(40) NULL, KontaktPerson varchar(35) NULL, GAdress varchar(40) NULL,
    GAdress2 varchar(50) NULL, PNr varchar(6) NULL, POrt varchar(25) NULL, TelefonNr1 varchar(15) NULL, EMail varchar(50) NULL
)
GO
CREATE TABLE dbo.Klartext (Kodtyp varchar(2) NOT NULL, Kod varchar(5) NOT NULL, Klartext varchar(50) NULL)
GO
INSERT INTO dbo.[Ägare] VALUES (10, '556677-8899', 'Testbolaget AB', 'Kontakt Person', 'Box 1', NULL, '85230', 'Sundsvall', '060-100', 'testbolaget@example.com')
INSERT INTO dbo.[Ägare] VALUES (11, '556677-0001', 'Förra ägaren AB', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
INSERT INTO dbo.Objekt VALUES (1, 10, '12345678', '12', 'Krogen Ett', 'Storgatan 1', '85230', 'Sundsvall', '060-1', NULL, '01', 'skylt.jpg', NULL)
INSERT INTO dbo.Objekt VALUES (2, 10, '12345679', '12', 'Krogen Två', 'Storgatan 2', '85230', 'Sundsvall', NULL, NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, Vin, AretRunt, ServeringAllmanhet, Uteservering, ServeringStartTid1, ServeringSlutTid1,
    Utfardande_Datum, Utfardande_Diarienr, GTRestaurangBeteckning, BeslutsID, Noteringar)
    VALUES (1, 10, 1, 1, 1, 1, 1, '11:00', '01:00', '2019-05-01', 'IAN-2019-1', '12345678', 100, 'Inomhus')
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, AretRunt, ServeringAllmanhet, Utfardande_Datum, Utfardande_Diarienr)
    VALUES (2, 11, 1, 1, 1, '2020-01-01', 'IAN-2020-2')
INSERT INTO dbo.ObjVillkor (Notering, ObjID, Villkor) VALUES ('Mat ska serveras', 1, '0100')
INSERT INTO dbo.[Ärende] VALUES (500, 1, '01', 'IAN-2019-1', '2019-01-01', '2019-05-01', NULL)
INSERT INTO dbo.[Ärende] VALUES (700, 1, '07', 'TIL-2021-7', '2021-01-01', '2021-03-01', 70)
INSERT INTO dbo.[Ärende_Beslut] VALUES (100, 500, '0100', '2019-04-30', NULL, '22222222.rtf', NULL, 'TDB')
INSERT INTO dbo.[Ärende_Händelser] VALUES (1, 500, '11111111.rtf', 'ansokan.pdf', NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Ärende_Händelser] VALUES (2, 500, NULL, 'saknas.pdf', NULL, NULL, NULL, NULL)
-- The certificate of permit 1, which also goes on the permit in PartyAssets, and an event of the place without an errand (decisions 34 and 37)
INSERT INTO dbo.[Ärende_Händelser] VALUES (4, 500, '55555555.rtf', NULL, NULL, 'TDBV2', '2019-05-02T13:45:00', NULL)
INSERT INTO dbo.[Ärende_Händelser] VALUES (5, NULL, NULL, 'serveringsansvarig.pdf', 1, NULL, '2020-01-01', NULL)
INSERT INTO dbo.[Sökande] VALUES (70, '5566778899', 'Testbolaget AB', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Tolvan Tolvansson', '191212121212', 'VD', 0, 0)
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Avgången Ledamot', '191212121212', 'LE', 1, 0)
-- An owning company among the persons, which is looked up as a company
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Ägarbolaget AB', '559988-7766', 'ÄB', 0, 0)
INSERT INTO dbo.Klartext VALUES ('A', '07', 'Tillsyn, ifrågasatt åtgärd')
INSERT INTO dbo.Klartext VALUES ('B', '0100', 'Serveringstillstånd')
INSERT INTO dbo.Klartext VALUES ('G', '01', 'Centrum')
INSERT INTO dbo.Klartext VALUES ('O', '0100', 'Krav på bordsservering')
INSERT INTO dbo.Klartext VALUES ('P', 'VD', 'Verkställande direktör')
INSERT INTO dbo.Klartext VALUES ('P', 'ÄB', 'Ägarbolag')
INSERT INTO dbo.Klartext VALUES ('Q', '12', 'Kvartersrestaurang')
-- A place whose files have Swedish letters in their names, in every table that names files, for the run interrupted at the activation
INSERT INTO dbo.Objekt VALUES (3, 10, '12345680', '12', 'Krogen Tre', 'Storgatan 3', '85230', 'Sundsvall', NULL, NULL, '01', 'Översiktskarta.pdf', NULL)
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, AretRunt, ServeringAllmanhet, ServeringStartTid1, ServeringSlutTid1, Utfardande_Datum, Utfardande_Diarienr,
    GTRestaurangBeteckning, BeslutsID)
    VALUES (3, 10, 1, 1, 1, '11:00', '23:00', '2021-02-01', 'IAN-2021-3', '12345680', 101)
INSERT INTO dbo.[Ärende] VALUES (503, 3, '01', 'IAN-2021-3', '2020-12-01', '2021-02-01', NULL)
INSERT INTO dbo.[Ärende_Beslut] VALUES (101, 503, '0100', '2021-01-31', NULL, '44444444.rtf', NULL, 'TDB')
INSERT INTO dbo.[Ärende_Händelser] VALUES (3, 503, NULL, 'ansökan räkenskaper.pdf', NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Ärende_Händelser_Filer] (HandelseID, Bifogadfil) VALUES (3, 'ritning våning 2.pdf')
INSERT INTO dbo.[Ärende_Bilder] (ArendeID, BildDatumTid, Bildfilnamn) VALUES (503, '2019-06-10T10:00:00', 'fasad.jpg')
-- A place in the area Timrå of AlkT, which belongs to another municipality (decision 19)
INSERT INTO dbo.Objekt VALUES (4, 10, '12345681', '12', 'Timråkrogen', 'Köpmangatan 4', '86130', 'Timrå', NULL, NULL, 'G15', NULL, NULL)
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, AretRunt, ServeringAllmanhet, Utfardande_Datum, Utfardande_Diarienr, GTRestaurangBeteckning)
    VALUES (4, 10, 1, 1, 1, '2022-03-01', 'IAN-2022-4', '12345681')
INSERT INTO dbo.Klartext VALUES ('G', 'G15', 'Timrå')
-- Inspection errands of the place of the permit, one whose relation exists and one with two relations
INSERT INTO dbo.[Ärende] VALUES (701, 1, '08', 'TIL-2022-8', '2022-01-01', '2022-02-01', 70)
INSERT INTO dbo.[Ärende] VALUES (702, 1, '09', 'TIL-2023-9', '2023-01-01', '2023-02-01', 70)
-- An errand of another place that refers to the same file name as the place of permit 1 (decision 26)
INSERT INTO dbo.[Ärende] VALUES (504, 2, '10', 'OVR-2020-5', '2020-05-01', '2020-06-01', NULL)
INSERT INTO dbo.[Ärende_Händelser] VALUES (9, 504, NULL, 'skylt.jpg', NULL, NULL, NULL, NULL)
INSERT INTO dbo.Klartext VALUES ('A', '08', 'Tillsyn, besök')
INSERT INTO dbo.Klartext VALUES ('A', '09', 'Tillsyn, inre')
GO
-- Inspection visits (section 7.7.2): one of the place of permit 1, with a remark and files, and one of a place without a permit, which is not migrated
CREATE TABLE dbo.[Tillsyn_Tillfälle] (
    TillsynsTillfalleNr int NOT NULL PRIMARY KEY, ObjektId int NULL, StartDatum datetime NULL, StartTid varchar(10) NULL, SlutTid varchar(10) NULL,
    Tillsynsform varchar(5) NULL, Fritext varchar(2000) NULL, WordDokument varchar(50) NULL, Bifogadfil varchar(100) NULL
)
GO
CREATE TABLE dbo.[Tillsyn_Anmärkningar] (
    TillsynstillfalleNr int NOT NULL, Anmarkningskod varchar(5) NOT NULL, Notering varchar(200) NULL, Bifogadfil varchar(100) NULL
)
GO
CREATE TABLE dbo.[Tillsyn_Tillfälle_Filer] (ID int IDENTITY(1, 1) NOT NULL, TillsynsTillfalleNr int NULL, Bifogadfil varchar(255) NULL)
GO
INSERT INTO dbo.[Tillsyn_Tillfälle] VALUES (900, 1, '2023-11-04', '21.30', '23:00', '04', 'Mycket folk i baren.', NULL, 'besöksrapport.pdf')
INSERT INTO dbo.[Tillsyn_Tillfälle] VALUES (901, 99, '2023-11-04', NULL, NULL, '04', NULL, NULL, NULL)
INSERT INTO dbo.[Tillsyn_Anmärkningar] VALUES (900, '05', 'Ungdomar i baren', NULL)
INSERT INTO dbo.[Tillsyn_Anmärkningar] VALUES (900, '06', NULL, NULL)
INSERT INTO dbo.[Tillsyn_Tillfälle_Filer] (TillsynsTillfalleNr, Bifogadfil) VALUES (900, 'foto.jpg')
INSERT INTO dbo.Klartext VALUES ('L', '04', 'Rutintillsyn')
INSERT INTO dbo.Klartext VALUES ('V', '05', 'Servering till minderårig')
INSERT INTO dbo.Klartext VALUES ('V', '06', 'Servering utom tillåten serveringstid')
GO
-- The texts of the errands, their events, decisions and pictures, and of an event of a place without an errand (decisions 44, 46 and 47)
ALTER TABLE dbo.[Ärende] ADD Noteringar varchar(500) NULL, Noteringar2 varchar(500) NULL, Noteringar3 varchar(500) NULL, Noteringar4 varchar(500) NULL,
    Noteringar5 varchar(3000) NULL, Noteringar5B varchar(500) NULL, Noteringar6 varchar(500) NULL, Noteringar7 varchar(500) NULL, Noteringar8 varchar(500) NULL,
    ArendeNotat varchar(50) NULL
ALTER TABLE dbo.[Ärende_Beslut] ADD BeslutsText varchar(1500) NULL, BeslutsText2 varchar(3000) NULL
ALTER TABLE dbo.[Ärende_Händelser] ADD HandelseText varchar(MAX) NULL
ALTER TABLE dbo.[Ärende_Bilder] ADD BildText varchar(1000) NULL
GO
UPDATE dbo.[Ärende] SET Noteringar = 'Ansökan komplett' WHERE ArendeID = 500
UPDATE dbo.[Ärende] SET Noteringar5 = 'Klagomål från grannar' WHERE ArendeID = 700
UPDATE dbo.[Ärende_Beslut] SET BeslutsText = 'Tillstånd beviljas.' WHERE BeslutsID = 100
INSERT INTO dbo.[Ärende_Händelser] (HandelseID, ArendeID, ObjektID, HandelseTyp, HandelseDatumTid, HandelseText) VALUES (10, 700, 1, 'ANT', '2021-01-15T10:00:00', 'Samtal med bolaget')
INSERT INTO dbo.[Ärende_Händelser] (HandelseID, ArendeID, ObjektID, HandelseTyp, HandelseDatumTid, HandelseText) VALUES (11, NULL, 1, 'ANT', '2020-02-01T08:00:00', 'Samtal om öppettider')
INSERT INTO dbo.Klartext VALUES ('D', 'ANT', 'Anteckning')
GO
-- The second decision text, a picture with only a text, and the handlers and marks about the serving staff of the visit (decisions 44 and 48)
ALTER TABLE dbo.[Tillsyn_Tillfälle] ADD Handl1 varchar(5) NULL, Handl2 varchar(5) NULL, Handl3 varchar(5) NULL, ServPersEjKollat bit NULL, ServPersEjpaPlats bit NULL,
    ServPersAgarePaPlats bit NULL
CREATE TABLE dbo.[Handläggare] (HandlaggarID varchar(5) NOT NULL PRIMARY KEY, Namn varchar(40) NULL)
GO
UPDATE dbo.[Ärende_Beslut] SET BeslutsText2 = 'Med villkor om bordsservering.' WHERE BeslutsID = 100
INSERT INTO dbo.[Ärende_Bilder] (ArendeID, BildDatumTid, Bildfilnamn, BildText) VALUES (700, '2021-01-20T10:00:00', NULL, 'Fasaden mot torget')
-- XYZ has no name in Handläggare, so the visit names that handler by the code
UPDATE dbo.[Tillsyn_Tillfälle] SET Handl1 = 'ABE', Handl2 = 'XYZ', ServPersAgarePaPlats = 1, ServPersEjKollat = 0, ServPersEjpaPlats = 1 WHERE TillsynsTillfalleNr = 900
INSERT INTO dbo.[Handläggare] VALUES ('ABE', 'Anna Berg')
GO
-- The handlers of the errands, who made the decision, a confidential event, an event without a text and the round of inspection of the visit
-- (decisions 50-53). XYZ has no name in Handläggare, so the errand names that handler by the code
ALTER TABLE dbo.[Ärende] ADD HandlaggarID varchar(5) NULL, HandlaggarID2 varchar(5) NULL
ALTER TABLE dbo.[Ärende_Beslut] ADD BeslutsFattare varchar(5) NULL
ALTER TABLE dbo.[Ärende_Händelser] ADD Sekretess bit NOT NULL CONSTRAINT DF_Arende_Handelser_Sekretess DEFAULT 0
ALTER TABLE dbo.[Tillsyn_Tillfälle] ADD TillsynsOmgangsNr int NULL
CREATE TABLE dbo.[Tillsyn_Omgång] (TillsynsOmgangsID int NOT NULL PRIMARY KEY, Omgangsnamn varchar(40) NULL, StartDatum datetime NULL, SlutDatum datetime NULL, Fritext varchar(1000) NULL)
GO
UPDATE dbo.[Ärende] SET HandlaggarID = 'ABE' WHERE ArendeID = 500
UPDATE dbo.[Ärende] SET HandlaggarID = 'ABE', HandlaggarID2 = 'XYZ' WHERE ArendeID = 700
UPDATE dbo.[Ärende_Beslut] SET BeslutsFattare = 'ABE' WHERE BeslutsID = 100
UPDATE dbo.[Ärende_Händelser] SET Sekretess = 1 WHERE HandelseID = 10
INSERT INTO dbo.[Ärende_Händelser] (HandelseID, ArendeID, ObjektID, HandelseTyp, HandelseDatumTid) VALUES (12, 700, 1, 'REMP', '2021-01-18T09:00:00')
INSERT INTO dbo.Klartext VALUES ('D', 'REMP', 'Remiss till polisen')
INSERT INTO dbo.[Tillsyn_Omgång] VALUES (40, 'Krogrunda', '2023-11-04', '2023-11-05', 'Tillsammans med polisen.')
UPDATE dbo.[Tillsyn_Tillfälle] SET TillsynsOmgangsNr = 40 WHERE TillsynsTillfalleNr = 900
GO
-- The diary number of an event, which the journal shows when it is another than that of its errand (decision 56)
ALTER TABLE dbo.[Ärende_Händelser] ADD DiarieNr varchar(30) NULL
GO
UPDATE dbo.[Ärende_Händelser] SET DiarieNr = 'TIL-2021-7' WHERE HandelseID = 10
UPDATE dbo.[Ärende_Händelser] SET DiarieNr = 'TIL-2020-3' WHERE HandelseID = 12
GO
-- The end day of a visit and the day an errand was registered, which complete the dates of the investigations (decision 59)
ALTER TABLE dbo.[Tillsyn_Tillfälle] ADD SlutDatum datetime NULL
ALTER TABLE dbo.[Ärende] ADD UpplagdDatum datetime NULL
GO
UPDATE dbo.[Tillsyn_Tillfälle] SET SlutDatum = '2023-11-04' WHERE TillsynsTillfalleNr = 900
UPDATE dbo.[Ärende] SET UpplagdDatum = '2020-12-28' WHERE ArendeID = 700
GO
-- The first day a decision is valid, which the decision of the permit errand gets (decision 70)
ALTER TABLE dbo.[Ärende_Beslut] ADD GiltigtFROM_Datum datetime NULL
GO
UPDATE dbo.[Ärende_Beslut] SET GiltigtFROM_Datum = '2019-05-01' WHERE BeslutsID = 100
GO
-- An event of place 1 without an errand, with a text, a document and an attachment, which becomes a documentation errand filed
-- under the permit of the place (decision 71)
INSERT INTO dbo.[Ärende_Händelser] (HandelseID, ArendeID, ObjektID, HandelseTyp, HandelseDatumTid, HandelseText, Worddokument, Bifogadfil, Dokument)
    VALUES (31020, NULL, 1, 'ANT', '2026-10-13T10:00:00', 'ANDREAS TESTAR', '31020000.rtf', 'testbilaga.pdf', NULL)
GO
-- Only the current holder follows (decision 72): the issuing errand of permit 1 was applied for by the owner, and an
-- inspection errand of a former owner and an event of its time are not migrated (G32)
UPDATE dbo.[Ärende] SET SokandeID = 70 WHERE ArendeID = 500
INSERT INTO dbo.[Sökande] VALUES (71, '556677-0001', 'Förra ägaren AB', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Ärende] (ArendeID, ObjektID, ArendeTyp, DiarieNr, OppnandeDatum, AvslutsDatum, SokandeID) VALUES (703, 1, '07', 'TIL-2018-7', '2018-03-01', '2018-04-01', 71)
INSERT INTO dbo.[Ärende_Händelser] (HandelseID, ArendeID, ObjektID, HandelseTyp, HandelseDatumTid, HandelseText) VALUES (13, NULL, 1, 'ANT', '2018-03-15T09:00:00', 'Samtal med förra ägaren')
GO
-- The serving staff of place 1, of whom one is written twice and one is passive, and a person of the staff recorded at the
-- visit, which says that someone responsible was on site (decisions 79 and 80). Personnr is there but not read
CREATE TABLE dbo.Objektpersonal (ObjPersID int IDENTITY(1, 1) NOT NULL PRIMARY KEY, ObjektID int NULL, Namn varchar(50) NULL, Personnr varchar(20) NULL, FROMDatum datetime NULL, PASSIV bit NULL)
CREATE TABLE dbo.Tillsyn_Servpers (TFSID int IDENTITY(1, 1) NOT NULL PRIMARY KEY, TillsynsTillfalleNr int NULL, Namn varchar(50) NULL, Personnr varchar(20) NULL)
GO
INSERT INTO dbo.Objektpersonal (ObjektID, Namn, Personnr, FROMDatum, PASSIV) VALUES (1, 'Olle Svensson', '800101-1234', '2021-06-01', NULL)
INSERT INTO dbo.Objektpersonal (ObjektID, Namn, Personnr, FROMDatum, PASSIV) VALUES (1, 'Lisa Åberg', '850505-5555', NULL, 0)
INSERT INTO dbo.Objektpersonal (ObjektID, Namn, Personnr, FROMDatum, PASSIV) VALUES (1, 'olle svensson', '800101-1234', '2015-03-01', NULL)
INSERT INTO dbo.Objektpersonal (ObjektID, Namn, Personnr, FROMDatum, PASSIV) VALUES (1, 'Slutat Person', '700101-1111', '2010-01-01', 1)
INSERT INTO dbo.Objektpersonal (ObjektID, Namn, Personnr, FROMDatum, PASSIV) VALUES (99, 'Annat Ställe', '900101-2222', '2020-01-01', NULL)
INSERT INTO dbo.Tillsyn_Servpers (TillsynsTillfalleNr, Namn, Personnr) VALUES (900, 'Olle Svensson', '800101-1234')
UPDATE dbo.[Tillsyn_Tillfälle] SET ServPersEjpaPlats = 0 WHERE TillsynsTillfalleNr = 900
GO
