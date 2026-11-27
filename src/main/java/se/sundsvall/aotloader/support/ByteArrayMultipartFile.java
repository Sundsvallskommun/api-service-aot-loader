package se.sundsvall.aotloader.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

/**
 * A file held in memory, to upload as a part of a multipart request.
 */
public final class ByteArrayMultipartFile implements MultipartFile {

	private final String name;
	private final String originalFilename;
	private final String contentType;
	private final byte[] content;

	public ByteArrayMultipartFile(final String name, final String originalFilename, final byte[] content) {
		this.name = name;
		this.originalFilename = originalFilename;
		this.contentType = Optional.ofNullable(URLConnection.guessContentTypeFromName(originalFilename)).orElse(APPLICATION_OCTET_STREAM_VALUE);
		this.content = content.clone();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getOriginalFilename() {
		return originalFilename;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return content.length == 0;
	}

	@Override
	public long getSize() {
		return content.length;
	}

	@Override
	public byte[] getBytes() {
		return content.clone();
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(content);
	}

	@Override
	public void transferTo(final File destination) throws IOException {
		throw new UnsupportedOperationException("The file is only ever uploaded, never written to disk");
	}
}
