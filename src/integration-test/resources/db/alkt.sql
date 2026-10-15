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
    Utfardande_Datum, Utfardande_Diarienr, GTRestaurangBeteckning, BeslutsID)
    VALUES (1, 10, 1, 1, 1, 1, 1, '11:00', '01:00', '2019-05-01', 'IAN-2019-1', '12345678', 100)
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, AretRunt, ServeringAllmanhet, Utfardande_Datum, Utfardande_Diarienr)
    VALUES (2, 11, 1, 1, 1, '2020-01-01', 'IAN-2020-2')
INSERT INTO dbo.ObjVillkor (Notering, ObjID, Villkor) VALUES ('Mat ska serveras', 1, '0100')
INSERT INTO dbo.[Ärende] VALUES (500, 1, '01', 'IAN-2019-1', '2019-01-01', '2019-05-01', NULL)
INSERT INTO dbo.[Ärende] VALUES (700, 1, '07', 'TIL-2021-7', '2021-01-01', '2021-03-01', 70)
INSERT INTO dbo.[Ärende_Beslut] VALUES (100, 500, 'GT', '2019-04-30', NULL, '22222222.rtf', NULL, 'TDB')
INSERT INTO dbo.[Ärende_Händelser] VALUES (1, 500, '11111111.rtf', 'ansokan.pdf', NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Ärende_Händelser] VALUES (2, 500, NULL, 'saknas.pdf', NULL, NULL, NULL, NULL)
-- The certificate of permit 1, which also goes on the permit in PartyAssets, and an event of the place without an errand (decisions 34 and 37)
INSERT INTO dbo.[Ärende_Händelser] VALUES (4, 500, '55555555.rtf', NULL, NULL, 'TDBV2', '2019-05-02T13:45:00', NULL)
INSERT INTO dbo.[Ärende_Händelser] VALUES (5, NULL, NULL, 'serveringsansvarig.pdf', 1, NULL, '2020-01-01', NULL)
INSERT INTO dbo.[Sökande] VALUES (70, '5566778899', 'Testbolaget AB', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Tolvan Tolvansson', '191212121212', 'VD', 0, 0)
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Avgången Ledamot', '191212121212', 'LE', 1, 0)
INSERT INTO dbo.Klartext VALUES ('A', '07', 'Tillsyn, ifrågasatt åtgärd')
INSERT INTO dbo.Klartext VALUES ('B', 'GT', 'Serveringstillstånd')
INSERT INTO dbo.Klartext VALUES ('G', '01', 'Centrum')
INSERT INTO dbo.Klartext VALUES ('O', '0100', 'Krav på bordsservering')
INSERT INTO dbo.Klartext VALUES ('P', 'VD', 'Verkställande direktör')
INSERT INTO dbo.Klartext VALUES ('Q', '12', 'Kvartersrestaurang')
-- A place whose files have Swedish letters in their names, in every table that names files, for the run interrupted at the activation
INSERT INTO dbo.Objekt VALUES (3, 10, '12345680', '12', 'Krogen Tre', 'Storgatan 3', '85230', 'Sundsvall', NULL, NULL, '01', 'Översiktskarta.pdf', NULL)
INSERT INTO dbo.[Gällande_Tillstånd] (ObjektID, GTAgarID, Starkol, AretRunt, ServeringAllmanhet, ServeringStartTid1, ServeringSlutTid1, Utfardande_Datum, Utfardande_Diarienr,
    GTRestaurangBeteckning, BeslutsID)
    VALUES (3, 10, 1, 1, 1, '11:00', '23:00', '2021-02-01', 'IAN-2021-3', '12345680', 101)
INSERT INTO dbo.[Ärende] VALUES (503, 3, '01', 'IAN-2021-3', '2020-12-01', '2021-02-01', NULL)
INSERT INTO dbo.[Ärende_Beslut] VALUES (101, 503, 'GT', '2021-01-31', NULL, '44444444.rtf', NULL, 'TDB')
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
