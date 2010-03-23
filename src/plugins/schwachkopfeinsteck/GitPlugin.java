package plugins.schwachkopfeinsteck;

import java.io.File;
import java.io.IOException;

import plugins.schwachkopfeinsteck.daemon.AnonymousGitDaemon;
import plugins.schwachkopfeinsteck.toadlets.AdminToadlet;
import plugins.schwachkopfeinsteck.toadlets.BrowseToadlet;
import plugins.schwachkopfeinsteck.toadlets.RepositoriesToadlet;

import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class GitPlugin implements FredPlugin, FredPluginL10n, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {

		private static volatile boolean logMINOR;
		private static volatile boolean logDEBUG;

		static {
			Logger.registerClass(GitPlugin.class);
		}

		public static final String PLUGIN_URI = "/GitPlugin";
		private static final String PLUGIN_CATEGORY = "Git Tools";
		public static final String PLUGIN_TITLE = "Git Plugin";

		private PluginContext pluginContext;
		private WebInterface webInterface;

		private AnonymousGitDaemon simpleDaemon;
		private RepositoryManager repositoryManager;

		public void runPlugin(PluginRespirator pluginRespirator) {
			pluginContext = new PluginContext(pluginRespirator);

			final File cacheDir;
			try {
				cacheDir = RepositoryManager.ensureCacheDirExists("./gitcache");
			} catch (IOException e) {
				// It makes really no sense without cache, blow away.
				throw new Error(e);
			}
	
			repositoryManager = new RepositoryManager(cacheDir, pluginContext);
			// for now a single server only, later versions can do multiple servers
			// and each can have its own cache/config
			simpleDaemon = new AnonymousGitDaemon("huhu", repositoryManager, pluginContext);

			webInterface = new WebInterface(pluginContext);
			webInterface.addNavigationCategory(PLUGIN_URI+"/", PLUGIN_CATEGORY, "Git Toolbox", this);

			// Visible pages
			BrowseToadlet browseToadlet = new BrowseToadlet(pluginContext, simpleDaemon);
			webInterface.registerVisible(browseToadlet, PLUGIN_CATEGORY, "Browse", "Browse a git repository like GitWeb");
			AdminToadlet adminToadlet = new AdminToadlet(pluginContext, simpleDaemon, repositoryManager);
			webInterface.registerVisible(adminToadlet, PLUGIN_CATEGORY, "Admin", "Admin the git server");
			RepositoriesToadlet reposToadlet = new RepositoriesToadlet(pluginContext, simpleDaemon, repositoryManager);
			webInterface.registerVisible(reposToadlet, PLUGIN_CATEGORY, "Repositories", "Create & Delete server's repositories");
		}

		public void terminate() {
			if (simpleDaemon.isRunning())
				simpleDaemon.stop();
			simpleDaemon = null;
			webInterface.kill();
			webInterface = null;
			repositoryManager.kill();
			repositoryManager = null;
		}

		public String getString(String key) {
			// return the key for now, l10n comes later
			return key;
		}

		public void setLanguage(LANGUAGE newLanguage) {
			// ignored for now, l10n comes later
		}

		public String getVersion() {
			return Version.getLongVersionString();
		}

		public long getRealVersion() {
			return Version.getRealVersion();
		}

}
