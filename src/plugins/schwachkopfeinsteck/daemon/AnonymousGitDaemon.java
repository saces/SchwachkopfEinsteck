package plugins.schwachkopfeinsteck.daemon;

import java.io.File;

import freenet.support.Executor;
import freenet.support.incubation.server.AbstractServer;
import freenet.support.incubation.server.AbstractService;

public class AnonymousGitDaemon extends AbstractServer {

	private File reposdir;

	public AnonymousGitDaemon(String servername, Executor executor) {
		super(servername, executor);
	}

	@Override
	protected AbstractService getService() {
		return new AnonymousGitService();
	}

	public void setCacheDir(String dir) {
		if (reposdir != null) {
			// move
		} else {
			File newDir = new File(dir);
			if (!newDir.exists()) {
				newDir.mkdirs();
			}
			reposdir = newDir;
		}
	}

	public String getCacheDir() {
		return reposdir.getPath();
	}

}
