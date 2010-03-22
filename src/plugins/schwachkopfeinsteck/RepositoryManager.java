package plugins.schwachkopfeinsteck;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.lib.Repository;

import freenet.node.useralerts.SimpleUserAlert;
import freenet.node.useralerts.UserAlert;
import freenet.support.Logger;
import freenet.support.io.FileUtil;

public class RepositoryManager {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(GitPlugin.class);
	}

	private final File cacheDir;

	RepositoryManager(File cachedir) {
		cacheDir = cachedir;
	}

	public static File ensureCacheDirExists(String dir) throws IOException {
		File newDir = new File(dir);
		if (newDir.exists()) {
			return newDir;
		}
		if (newDir.mkdirs()) {
			return newDir;
		}
		throw new IOException("Unable to create cache directory: "+newDir.getAbsolutePath());
	}

	public String getCacheDir() {
		return cacheDir.getPath();
	}

	public void deleteRepository(String reposName) {
		File repos = new File(cacheDir, reposName);
		FileUtil.removeAll(repos);
	}

	public void updateDescription(String repos, String desc) throws IOException {
		File reposFile = new File(cacheDir, repos);
		updateDescription(reposFile, desc);
	}

	private void updateDescription(File repos, String desc) throws IOException {
		File descfile = new File(repos, "description");
		if (descfile.exists()) {
			descfile.delete();
		}
		InputStream is = new ByteArrayInputStream(desc.getBytes("UTF-8"));
		FileUtil.writeTo(is, descfile);
	}

	private void tryCreateRepository(String reposName) throws IOException {
		tryCreateRepository(reposName, null);
	}

	public void tryCreateRepository(String reposName, String description) throws IOException {
		File reposDir = new File(cacheDir, reposName);
		Repository repos;
		repos = new Repository(reposDir);
		repos.create(true);
		if (description != null) {
			updateDescription(reposDir, description);
		}
	}

	@Deprecated
	public File getCacheDirFile() {
		return cacheDir;
	}

	public void kill() {
		// TODO stopp lockking, kill all jobs.
		// empty caches.
	}
}
