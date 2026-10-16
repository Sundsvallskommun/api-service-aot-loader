package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * A reference in a source database to a file on the file area. The databases store the file name only; the folder
 * tells which directory of the file area the file is in.
 *
 * @param name                   the file name as the database has it
 * @param folder                 the directory of the file area
 * @param sharedWithAnotherPlace whether another place of the source refers to a file with the same name in the same
 *                               directory; the directories are flat, so the file may belong to the other place
 */
public record FileReference(String name, FileFolder folder, boolean sharedWithAnotherPlace) {

	public FileReference {
		requireNonNull(name, "name");
		requireNonNull(folder, "folder");
	}

	/**
	 * A reference whose name no other place of the source uses.
	 */
	public FileReference(final String name, final FileFolder folder) {
		this(name, folder, false);
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
