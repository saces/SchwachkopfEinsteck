package plugins.schwachkopfeinsteck;

import java.io.IOException;
import java.net.URI;

import plugins.schwachkopfeinsteck.daemon.AnonymousGitDaemon;

import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

public class BrowseToadlet extends WebInterfaceToadlet {

	private static final String PARAM_URI = "URI";
	private static final String CMD_BROWSE = "browse";

	private final AnonymousGitDaemon daemon;

	public BrowseToadlet(PluginContext context, AnonymousGitDaemon simpleDaemon) {
		super(context, GitPlugin.PLUGIN_URI, "");
		daemon = simpleDaemon;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if (!normalizePath(req.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		makePage(ctx);
	}

	private void makePage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Browse git repos", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Browsa a git repos", contentNode);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "uriForm");
		boxForm.addChild("#", "Not implemented yet.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Repos URI: \u00a0 ");
		boxForm.addChild("input", new String[] { "type", "name", "size"}, new String[] { "text", PARAM_URI, "70" });
		boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_BROWSE, "Open", "disabled" });

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}


}
