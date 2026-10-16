-- The tables of AlkOL (tobacco, folköl, e-cigarettes and nicotine products) that the migration reads, with only the columns it reads, and made up rows. FS_GT_Tobak
-- is a view in the real database; a table stands in for it here. No row holds real data.
CREATE TABLE dbo.[FörsäljningsStällen] (
    FSID int NOT NULL PRIMARY KEY, Namn varchar(100) NULL, GatuAdress nvarchar(50) NULL, PostNummer nvarchar(10) NULL, PostOrt nvarchar(30) NULL,
    Telefon1 nvarchar(50) NULL, Email nvarchar(50) NULL, [Område] varchar(50) NULL, Kategori nvarchar(50) NULL, [ÄgareOrgNr] nvarchar(50) NULL,
    Notering varchar(6000) NULL, OLTYP bit NOT NULL DEFAULT 0, TOBAKTYP bit NOT NULL DEFAULT 0, ECIGTYP bit NOT NULL DEFAULT 0,
    TobaksfriNikotinTyp bit NOT NULL DEFAULT 0, Upphort bit NULL, Agarid int NULL
)
GO
CREATE TABLE dbo.FS_GT_Tobak (FSID int NULL, GTDE bit NULL, GTPH bit NULL, GTDI bit NULL)
GO
CREATE TABLE dbo.[Ägare] (
    AgarID int NOT NULL PRIMARY KEY, OrganisationsNr varchar(12) NULL, Bolagsnamn varchar(100) NULL, KontaktPerson varchar(35) NULL, GAdress varchar(50) NULL,
    GAdress2 varchar(50) NULL, PNr varchar(6) NULL, POrt varchar(25) NULL, TelefonNr1 varchar(15) NULL, EMail varchar(50) NULL
)
GO
CREATE TABLE dbo.[Ärende] (
    ArendeID int NOT NULL PRIMARY KEY, ObjektID int NULL, Arendetyp varchar(50) NULL, DiarieNr varchar(50) NULL, OppnandeDatum datetime NULL,
    AvslutsDatum datetime NULL, Sokandeid int NULL
)
GO
CREATE TABLE dbo.Beslut (
    BeslutsID int NOT NULL PRIMARY KEY, ArendeID int NULL, BeslutsTyp varchar(8) NULL, BeslutsDatumTid datetime NULL, GiltigtFROM_Datum datetime NULL,
    GiltigtTOM_Datum datetime NULL, Worddokument varchar(12) NULL, Bifogadfil varchar(200) NULL, Dokument varchar(8) NULL
)
GO
CREATE TABLE dbo.[Händelser] (
    HandelseID int NOT NULL PRIMARY KEY, FSID int NULL, ArendeID int NULL, HandelseTyp nvarchar(8) NULL, HandelseDatumTid datetime NULL, DiarieNr varchar(30) NULL,
    Worddokument varchar(50) NULL, Bifogadfil varchar(200) NULL, Dokument nvarchar(8) NULL
)
GO
CREATE TABLE dbo.[Sökande] (
    SokandeID int NOT NULL PRIMARY KEY, OrganisationsNr varchar(12) NULL, Bolagsnamn varchar(50) NULL, KontaktPerson varchar(35) NULL, GAdress varchar(40) NULL,
    GAdress2 varchar(50) NULL, PNr varchar(6) NULL, POrt varchar(25) NULL, TelefonNr1 varchar(15) NULL, EMail varchar(50) NULL
)
GO
CREATE TABLE dbo.[PBI_ägare] (
    PBIAGID int IDENTITY(1, 1) NOT NULL, AgarID int NULL, Namn varchar(50) NULL, Personnr varchar(12) NULL, Befattningskod1 varchar(6) NULL,
    Befattningskod2 varchar(6) NULL, Befattningskod3 varchar(6) NULL, PASSIV bit NULL, UtlandskPerson bit NOT NULL DEFAULT 0
)
GO
CREATE TABLE dbo.Beslutskoder (ID int IDENTITY(1, 1) NOT NULL, Kod varchar(8) NULL, Klartext varchar(50) NULL, Passiv bit NULL)
GO
CREATE TABLE dbo.[Ärendetyper] (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL, Passiv bit NULL)
GO
CREATE TABLE dbo.Kategorier (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL, Passiv bit NULL)
GO
CREATE TABLE dbo.[Områden] (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL, Passiv bit NULL)
GO
CREATE TABLE dbo.PBI_Roller (ID int IDENTITY(1, 1) NOT NULL, Kod varchar(8) NULL, Klartext varchar(50) NULL, Passiv bit NULL)
GO
INSERT INTO dbo.[Ägare] VALUES (10, '556677-8899', 'Testbolaget AB', NULL, 'Box 1', NULL, '85230', 'Sundsvall', NULL, NULL)
INSERT INTO dbo.[FörsäljningsStällen] (FSID, Namn, GatuAdress, PostNummer, PostOrt, [Område], Kategori, [ÄgareOrgNr], Notering, OLTYP, TOBAKTYP, Upphort, Agarid)
    VALUES (1, 'Kiosken', 'Torget 1', '85230', 'Sundsvall', '12', 'KIO', '556677-8899', 'Öppet alla dagar', 1, 1, 0, 10)
INSERT INTO dbo.[FörsäljningsStällen] (FSID, Namn, GatuAdress, PostNummer, PostOrt, [Område], Kategori, [ÄgareOrgNr], Notering, OLTYP, TOBAKTYP, Upphort, Agarid)
    VALUES (2, 'Timråkiosken', 'Storgatan 2', '86130', 'Timrå', '13', 'KIO', '556677-8899', NULL, 1, 0, 0, 10)
INSERT INTO dbo.FS_GT_Tobak VALUES (1, 1, 0, 0)
INSERT INTO dbo.[Ärende] VALUES (100, 1, 'WADE', 'IAN-2019-100', '2019-06-01', '2019-07-01', NULL)
INSERT INTO dbo.Beslut VALUES (200, 100, 'GTDE', '2019-07-01T10:00:00', '2019-07-02', NULL, NULL, NULL, NULL)
INSERT INTO dbo.[Händelser] VALUES (300, 1, 100, '001', '2019-06-01T09:00:00', 'IAN-2019-100', '33333333.rtf', NULL, NULL)
INSERT INTO dbo.[Händelser] VALUES (301, 1, NULL, '014', '2015-03-01T09:00:00', 'IAN-2015-5', NULL, NULL, NULL)
INSERT INTO dbo.[Händelser] VALUES (302, 2, NULL, '014', '2020-03-01T09:00:00', 'IAN-2020-7', NULL, 'folkol-anmalan.pdf', NULL)
INSERT INTO dbo.[Sökande] VALUES (50, '5566778899', 'Testbolaget AB', NULL, 'Box 1', NULL, '85230', 'Sundsvall', NULL, NULL)
UPDATE dbo.[Ärende] SET Sokandeid = 50 WHERE ArendeID = 100
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Tolvan Tolvansson', '191212121212', 'VD', NULL, 0)
INSERT INTO dbo.PBI_Roller (Kod, Klartext) VALUES ('VD', 'VD')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES ('GTDE', 'Tillstånd tobaksförsäljning – detaljhandel')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES (NULL, 'Utan kod')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES ('UTAN', NULL)
INSERT INTO dbo.Kategorier (Kod, Klartext) VALUES ('KIO', 'Kiosk')
-- Kod is no key: a passive row of a code, inserted first, gives way to the active one
INSERT INTO dbo.[Områden] (Kod, Klartext, Passiv) VALUES ('12', 'Gamla city', 1)
INSERT INTO dbo.[Områden] (Kod, Klartext) VALUES ('12', 'City')
INSERT INTO dbo.[Områden] (Kod, Klartext) VALUES ('13', 'Timrå')
GO
-- An inspection visit of the sales place with a tobacco permit (section 7.7.2). AlkOL keeps the times as datetime.
CREATE TABLE dbo.[Tillsyn_Tillfälle] (
    TillsynsTillfalleNr int NOT NULL PRIMARY KEY, ObjektId int NULL, StartDatum datetime NULL, StartTid datetime NULL, SlutTid datetime NULL,
    Tillsynsform nvarchar(8) NULL, Fritext nvarchar(MAX) NULL, WordDokument varchar(50) NULL, Bifogadfil varchar(200) NULL
)
GO
CREATE TABLE dbo.[Tillsyn_Anmärkningar] (TillsynsTillfalleNr int NULL, Anmarkningskod nvarchar(8) NULL)
GO
CREATE TABLE dbo.[Tillsyn_Tillfälle_Filer] (ID int IDENTITY(1, 1) NOT NULL, TillsynsTillfalleNr int NULL, Bifogadfil varchar(255) NULL)
GO
CREATE TABLE dbo.TillsynsArt (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL, Passiv bit NULL)
GO
CREATE TABLE dbo.Anmarkningar (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL, Passiv bit NULL)
GO
INSERT INTO dbo.[Tillsyn_Tillfälle] VALUES (950, 1, '2024-05-03', '1899-12-30T14:00:00', '1899-12-30T14:20:00', 'Kontr', N'Köpet nekades.', NULL, NULL)
INSERT INTO dbo.[Tillsyn_Anmärkningar] VALUES (950, 'Rekl I')
INSERT INTO dbo.TillsynsArt (Kod, Klartext) VALUES ('Kontr', 'Kontrollköp')
INSERT INTO dbo.Anmarkningar (Kod, Klartext) VALUES ('Rekl I', N'Påträngande reklam i butik')
GO
-- The texts of the errands, their events and decisions, and of the events of a place without an errand (decisions 44, 46 and 47)
ALTER TABLE dbo.[Ärende] ADD Noteringar varchar(3000) NULL
ALTER TABLE dbo.Beslut ADD BeslutsText varchar(1500) NULL
ALTER TABLE dbo.[Händelser] ADD Handelsetext nvarchar(MAX) NULL
CREATE TABLE dbo.[Händelsetyper] (Kod varchar(8) NULL, Klartext varchar(50) NULL, Passiv bit NULL)
GO
UPDATE dbo.[Ärende] SET Noteringar = 'Komplett ansökan' WHERE ArendeID = 100
UPDATE dbo.Beslut SET BeslutsText = 'Tillstånd beviljas.' WHERE BeslutsID = 200
UPDATE dbo.[Händelser] SET Handelsetext = N'Ansökan inkom' WHERE HandelseID = 300
-- The event registers the folköl the place has notified, so its text is not in the log of the permit
UPDATE dbo.[Händelser] SET Handelsetext = N'Anmälan om folköl' WHERE HandelseID = 301
INSERT INTO dbo.[Händelser] (HandelseID, FSID, ArendeID, HandelseTyp, HandelseDatumTid, Handelsetext) VALUES (303, 1, NULL, '024', '2021-04-01T09:00:00', N'Samtal med kiosken')
INSERT INTO dbo.[Händelsetyper] (Kod, Klartext) VALUES ('024', 'Anteckningar')
GO
-- The handlers of the visit, named by the number of the user AnvID (decision 48). There is no user 7, and PE is the code
-- of a user, which the visits do not use
ALTER TABLE dbo.[Tillsyn_Tillfälle] ADD Handl1 nvarchar(5) NULL, Handl2 nvarchar(5) NULL, Handl3 nvarchar(5) NULL
CREATE TABLE dbo.[Användare] (AnvID int IDENTITY(1, 1) NOT NULL, [AnvändarKod] nvarchar(4) NULL, Namn nvarchar(50) NULL)
GO
UPDATE dbo.[Tillsyn_Tillfälle] SET Handl1 = '2', Handl2 = '7', Handl3 = 'PE' WHERE TillsynsTillfalleNr = 950
INSERT INTO dbo.[Användare] ([AnvändarKod], Namn) VALUES ('KA', N'Karin Andersson')
INSERT INTO dbo.[Användare] ([AnvändarKod], Namn) VALUES ('PE', N'Per Eriksson')
GO
-- The handlers of the errand by the numbers of the users, who made the decision by the code of the user, a confidential event, an event without a
-- text, and what the visit covered, that it was announced and its round of inspection (decisions 50-53)
ALTER TABLE dbo.[Ärende] ADD HandlaggarID int NULL, HandlaggarID2 int NULL
ALTER TABLE dbo.Beslut ADD BeslutsFattare varchar(5) NULL
ALTER TABLE dbo.[Händelser] ADD Sekretess bit NOT NULL CONSTRAINT DF_Handelser_Sekretess DEFAULT 0
ALTER TABLE dbo.[Tillsyn_Tillfälle] ADD TillsynsOmgangsNr int NULL, AnmaltBesok bit NULL, TTypOL bit NULL, TTypTOBAK bit NULL, TTypNIKOTIN bit NULL, TTypECIG bit NULL,
    TTypTobaksfriNikotin bit NOT NULL CONSTRAINT DF_Tillfalle_TTypTobaksfriNikotin DEFAULT 0
CREATE TABLE dbo.[Tillsyn_Omgång] (TillsynsOmgangsID int NOT NULL, Omgangsnamn nvarchar(30) NULL, StartDatum datetime NULL, SlutDatum datetime NULL, Fritext nvarchar(MAX) NULL)
GO
UPDATE dbo.[Ärende] SET HandlaggarID = 1 WHERE ArendeID = 100
UPDATE dbo.Beslut SET BeslutsFattare = 'PE' WHERE BeslutsID = 200
UPDATE dbo.[Händelser] SET Sekretess = 1 WHERE HandelseID = 303
INSERT INTO dbo.[Händelser] (HandelseID, FSID, ArendeID, HandelseTyp, HandelseDatumTid) VALUES (304, 1, 100, '017', '2019-06-15T09:00:00')
INSERT INTO dbo.[Händelsetyper] (Kod, Klartext) VALUES ('017', 'Annan utsänd handling')
UPDATE dbo.[Tillsyn_Tillfälle] SET TTypTOBAK = 1, TTypECIG = 1, AnmaltBesok = 1, TillsynsOmgangsNr = 1 WHERE TillsynsTillfalleNr = 950
INSERT INTO dbo.[Tillsyn_Omgång] VALUES (1, N'Kontrollköp vecka 18', '2024-05-03', '2024-05-03', NULL)
GO
-- The event without a text was registered in another case, which the journal shows (decision 56)
UPDATE dbo.[Händelser] SET DiarieNr = 'IAN-2019-99' WHERE HandelseID = 304
GO
