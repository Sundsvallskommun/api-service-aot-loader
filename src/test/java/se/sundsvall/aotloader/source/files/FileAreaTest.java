package se.sundsvall.aotloader.source.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

class FileAreaTest {

	@TempDir
	private Path documents;

	@TempDir
	private Path attachments;

	@Test
	void findsFilesByNameInTheirDirectory() throws IOException {
		Files.writeString(documents.resolve("12345678.rtf"), "dokument");
		Files.writeString(attachments.resolve("Ansokan.PDF"), "bilaga");
		Files.writeString(attachments.resolve("stor.pdf"), "x".repeat(20));
		Files.createDirectory(attachments.resolve("undermapp"));
		Files.writeString(attachments.resolve("undermapp").resolve("gomd.pdf"), "x");
		final var fileArea = new FileArea(new FileAreaDirectories(documents.toString(), attachments.toString()), 10);

		final var found = fileArea.find(new FileReference("12345678.rtf", DOCUMENTS));
		assertThat(found.result()).isEqualTo(LookupResult.FOUND);
		assertThat(found.size()).isEqualTo(8);
		assertThat(FileArea.read(found.path())).isEqualTo("dokument".getBytes());

		final var otherCase = fileArea.find(new FileReference("C:\\gammal\\sökväg\\ansokan.pdf", ATTACHMENTS));
		assertThat(otherCase.result()).isEqualTo(LookupResult.FOUND);
		assertThat(otherCase.name()).isEqualTo("Ansokan.PDF");

		assertThat(fileArea.find(new FileReference("12345678.rtf", ATTACHMENTS)).result()).isEqualTo(LookupResult.MISSING);
		assertThat(fileArea.find(new FileReference("gomd.pdf", ATTACHMENTS)).result()).isEqualTo(LookupResult.MISSING);
		assertThat(fileArea.find(new FileReference("../12345678.rtf", ATTACHMENTS)).result()).isEqualTo(LookupResult.MISSING);
		assertThat(fileArea.find(new FileReference("stor.pdf", ATTACHMENTS))).satisfies(lookup -> {
			assertThat(lookup.result()).isEqualTo(LookupResult.TOO_LARGE);
			assertThat(lookup.size()).isEqualTo(20);
		});
		assertThat(fileArea.find(new FileReference("länk.URL", ATTACHMENTS)).result()).isEqualTo(LookupResult.LINK);
		assertThat(fileArea.find(new FileReference("mapp/..", ATTACHMENTS)).result()).isEqualTo(LookupResult.INVALID_NAME);
		assertThat(fileArea.find(new FileReference("mapp/", ATTACHMENTS)).result()).isEqualTo(LookupResult.INVALID_NAME);
	}

	@Test
	void directoryThatCannotBeRead() {
		final var fileArea = new FileArea(new FileAreaDirectories(documents.resolve("finns-inte").toString(), null), 10);

		assertThat(fileArea.find(new FileReference("a.rtf", DOCUMENTS)).result()).isEqualTo(LookupResult.DIRECTORY_UNAVAILABLE);
		assertThat(fileArea.find(new FileReference("a.pdf", ATTACHMENTS)).result()).isEqualTo(LookupResult.DIRECTORY_UNAVAILABLE);
		assertThat(new FileArea(null, 10).find(new FileReference("a.pdf", ATTACHMENTS)).result()).isEqualTo(LookupResult.DIRECTORY_UNAVAILABLE);
	}

	@Test
	void checkDirectory() throws IOException {
		assertThat(FileArea.checkDirectory(documents.toString())).isEmpty();
		assertThat(FileArea.checkDirectory(" ")).contains("katalogen är inte konfigurerad");
		assertThat(FileArea.checkDirectory(documents.resolve("finns-inte").toString())).contains("katalogen finns inte eller går inte att läsa");
		final var file = Files.writeString(documents.resolve("fil.txt"), "x");
		assertThat(FileArea.checkDirectory(file.toString())).contains("katalogen finns inte eller går inte att läsa");
		assertThat(FileArea.checkDirectory("\u0000")).contains("katalogens sökväg är ogiltig");
	}

	@Test
	void fileNameOf() {
		assertThat(FileArea.fileNameOf(" a.pdf ")).isEqualTo("a.pdf");
		assertThat(FileArea.fileNameOf("x/y\\z.pdf")).isEqualTo("z.pdf");
		assertThat(FileArea.fileNameOf("..")).isNull();
		assertThat(FileArea.fileNameOf(" ")).isNull();
	}
}
