package plugins.schwachkopfeinsteck;

import java.net.InetSocketAddress;

import plugins.schwachkopfeinsteck.daemon.AnonymousGitDaemon;

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

		public void runPlugin(PluginRespirator pluginRespirator) {
			pluginContext = new PluginContext(pluginRespirator);
			simpleDaemon = new AnonymousGitDaemon("huhu", pluginContext.node.executor);

			webInterface = new WebInterface(pluginContext);
			webInterface.addNavigationCategory(PLUGIN_URI+"/", PLUGIN_CATEGORY, "Git Toolbox", this);

			// Visible pages
			BrowseToadlet browseToadlet = new BrowseToadlet(pluginContext, simpleDaemon);
			webInterface.registerVisible(browseToadlet, PLUGIN_CATEGORY, "Browse", "Browse a git repository like GitWeb");
			AdminToadlet adminToadlet = new AdminToadlet(pluginContext, simpleDaemon);
			webInterface.registerVisible(adminToadlet, PLUGIN_CATEGORY, "Admin", "Admin the git server");
			RepositoriesToadlet reposToadlet = new RepositoriesToadlet(pluginContext, simpleDaemon);
			webInterface.registerVisible(reposToadlet, PLUGIN_CATEGORY, "Repositories", "Create & Delete server's repositories");

			InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 9418);
			
			simpleDaemon.setCacheDir("./gitcache");
			//simpleDaemon.start(addr);
		}

		public void terminate() {
			webInterface.kill();
			if (simpleDaemon.isRunning())
				simpleDaemon.stop();
			simpleDaemon = null;
		}

		public String getString(String key) {
			return key;
		}

		public void setLanguage(LANGUAGE newLanguage) {
			// TODO Auto-generated method stub
			
		}

		public String getVersion() {
			return Version.getLongVersionString();
		}

		public long getRealVersion() {
			return Version.getRealVersion();
		}

}
