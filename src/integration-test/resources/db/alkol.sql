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
    GiltigtTOM_Datum datetime NULL, Worddokument varchar(12) NULL, Bifogadfil varchar(200) NULL
)
GO
CREATE TABLE dbo.[Händelser] (
    HandelseID int NOT NULL PRIMARY KEY, FSID int NULL, ArendeID int NULL, HandelseTyp nvarchar(8) NULL, HandelseDatumTid datetime NULL, DiarieNr varchar(30) NULL,
    Worddokument varchar(50) NULL, Bifogadfil varchar(200) NULL
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
CREATE TABLE dbo.Beslutskoder (ID int IDENTITY(1, 1) NOT NULL, Kod varchar(8) NULL, Klartext varchar(50) NULL)
GO
CREATE TABLE dbo.[Ärendetyper] (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL)
GO
CREATE TABLE dbo.Kategorier (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL)
GO
CREATE TABLE dbo.[Områden] (Kod nvarchar(8) NULL, Klartext nvarchar(50) NULL)
GO
CREATE TABLE dbo.PBI_Roller (ID int IDENTITY(1, 1) NOT NULL, Kod varchar(8) NULL, Klartext varchar(50) NULL)
GO
INSERT INTO dbo.[Ägare] VALUES (10, '556677-8899', 'Testbolaget AB', NULL, 'Box 1', NULL, '85230', 'Sundsvall', NULL, NULL)
INSERT INTO dbo.[FörsäljningsStällen] (FSID, Namn, GatuAdress, PostNummer, PostOrt, [Område], Kategori, [ÄgareOrgNr], Notering, OLTYP, TOBAKTYP, Upphort, Agarid)
    VALUES (1, 'Kiosken', 'Torget 1', '85230', 'Sundsvall', '12', 'KIO', '556677-8899', 'Öppet alla dagar', 1, 1, 0, 10)
INSERT INTO dbo.[FörsäljningsStällen] (FSID, Namn, GatuAdress, PostNummer, PostOrt, [Område], Kategori, [ÄgareOrgNr], Notering, OLTYP, TOBAKTYP, Upphort, Agarid)
    VALUES (2, 'Timråkiosken', 'Storgatan 2', '86130', 'Timrå', '13', 'KIO', '556677-8899', NULL, 1, 0, 0, 10)
INSERT INTO dbo.FS_GT_Tobak VALUES (1, 1, 0, 0)
INSERT INTO dbo.[Ärende] VALUES (100, 1, 'WADE', 'IAN-2019-100', '2019-06-01', '2019-07-01', NULL)
INSERT INTO dbo.Beslut VALUES (200, 100, 'GTDE', '2019-07-01T10:00:00', '2019-07-02', NULL, NULL, NULL)
INSERT INTO dbo.[Händelser] VALUES (300, 1, 100, '001', '2019-06-01T09:00:00', 'IAN-2019-100', '33333333.rtf', NULL)
INSERT INTO dbo.[Händelser] VALUES (301, 1, NULL, '014', '2015-03-01T09:00:00', 'IAN-2015-5', NULL, NULL)
INSERT INTO dbo.[Händelser] VALUES (302, 2, NULL, '014', '2020-03-01T09:00:00', 'IAN-2020-7', NULL, NULL)
INSERT INTO dbo.[Sökande] VALUES (50, '5566778899', 'Testbolaget AB', NULL, 'Box 1', NULL, '85230', 'Sundsvall', NULL, NULL)
UPDATE dbo.[Ärende] SET Sokandeid = 50 WHERE ArendeID = 100
INSERT INTO dbo.[PBI_ägare] (AgarID, Namn, Personnr, Befattningskod1, PASSIV, UtlandskPerson) VALUES (10, 'Tolvan Tolvansson', '191212121212', 'VD', NULL, 0)
INSERT INTO dbo.PBI_Roller (Kod, Klartext) VALUES ('VD', 'VD')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES ('GTDE', 'Tillstånd tobaksförsäljning – detaljhandel')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES (NULL, 'Utan kod')
INSERT INTO dbo.Beslutskoder (Kod, Klartext) VALUES ('UTAN', NULL)
INSERT INTO dbo.Kategorier VALUES ('KIO', 'Kiosk')
INSERT INTO dbo.[Områden] VALUES ('12', 'City')
INSERT INTO dbo.[Områden] VALUES ('13', 'Timrå')
GO
