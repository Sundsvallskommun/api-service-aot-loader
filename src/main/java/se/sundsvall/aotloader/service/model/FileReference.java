package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * A reference in a source database to a file on the file area. The databases store the file name only; the folder
 * tells which directory of the file area the file is in.
 *
 * @param name   the file name as the database has it
 * @param folder the directory of the file area
 */
public record FileReference(String name, FileFolder folder) {

	public FileReference {
		requireNonNull(name, "name");
		requireNonNull(folder, "folder");
	}

	/**
	 * The directories of a source's file area.
	 */
	public enum FileFolder {

		/** Documents that the old system generated. */
		DOCUMENTS,

		/** Attached files. */
		ATTACHMENTS
	}
}
