package ske;

import java.io.File;
import java.io.IOException;

import freenet.keys.FreenetURI;

public class Utils {

	public static File ensureCacheDirExists(String dir) throws IOException {
		File newDir = new File(dir);
		if (newDir.exists()) {
			if (!newDir.isDirectory()) {
				throw new IOException("Not a directory: "+newDir.getAbsolutePath());
			}
			return newDir;
		}
		if (newDir.mkdirs()) {
			return newDir;
		}
		throw new IOException("Unable to create cache directory: "+newDir.getAbsolutePath());
	}

	/**
	 * get the internal repository name from freenet uri.
	 * must be the request uri.
	 */
	public static String getRepositoryName(FreenetURI uri) {
		String docName = uri.getDocName();
		uri = uri.setKeyType("SSK");
		String reposName = uri.setDocName(null).setMetaString(null).toString(false, false);
		return new String(reposName + '@' + docName);
	}

}
