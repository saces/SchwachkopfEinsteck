package plugins.schwachkopfeinsteck.daemon;

import java.io.File;

import freenet.support.Executor;
import freenet.support.incubation.server.AbstractServer;
import freenet.support.incubation.server.AbstractService;
import freenet.support.plugins.helpers1.PluginContext;

public class AnonymousGitDaemon extends AbstractServer {

	private File reposdir;
	private boolean isReadOnly = true;
	private final PluginContext pluginContext;

	public AnonymousGitDaemon(String servername, Executor executor, PluginContext plugincontext) {
		super(servername, executor);
		pluginContext = plugincontext;
	}

	@Override
	protected AbstractService getService() {
		return new AnonymousGitService(isReadOnly(), eXecutor, pluginContext);
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

	public File getCacheDirFile() {
		return reposdir;
	}

	public void setReadOnly(boolean readOnly) {
		isReadOnly = readOnly;
	}

	public boolean isReadOnly() {
		return isReadOnly;
	}

}
