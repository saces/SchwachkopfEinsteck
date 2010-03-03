package plugins.schwachkopfeinsteck;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.schwachkopfeinsteck.daemon.AnonymousGitDaemon;

import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

public class AdminToadlet extends WebInterfaceToadlet {

	private static final String PARAM_PORT = "port";
	private static final String PARAM_BINDTO = "bindto";
	private static final String PARAM_ALLOWEDHOSTS = "allowedhosts";
	private static final String PARAM_CACHEDIR = "chachedir";
	private static final String PARAM_READONLY = "readonly";

	private static final String CMD_START = "start";
	private static final String CMD_STOP = "stop";
	private static final String CMD_RESTART = "restart";
	private static final String CMD_APPLY = "apply";

	private final AnonymousGitDaemon daemon;

	public AdminToadlet(PluginContext context, AnonymousGitDaemon simpleDaemon) {
		super(context, GitPlugin.PLUGIN_URI, "admin");
		daemon = simpleDaemon;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		List<String> errors = new LinkedList<String>();
		if (!normalizePath(req.getPath()).equals("/")) {
			errors.add("The path '"+uri+"' was not found");
		}
		makePage(ctx, errors);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		List<String> errors = new LinkedList<String>();

		if (!isFormPassword(request)) {
			errors.add("Invalid form password");
			makePage(ctx, errors);
			return;
		}

		String path = normalizePath(request.getPath());

		if (request.isPartSet(CMD_STOP)) {
			daemon.stop();
		} else {
			String bindTo = request.getPartAsString(PARAM_BINDTO, 50);
			int port = request.getIntPart(PARAM_PORT, 9481);
			String allowedHosts = request.getPartAsString(PARAM_ALLOWEDHOSTS, 50);
			String ro = request.getPartAsString(PARAM_READONLY, 50);
			if (request.isPartSet(CMD_START)) {
				daemon.setAdress(bindTo, port, allowedHosts, false);
				daemon.setReadOnly(ro.length() > 0);
				daemon.start();
			} else if (request.isPartSet(CMD_RESTART)) {
				daemon.stop();
				try {
					//sleep 3 sec, give the old bind a chance to vanish…
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					// ignored
				}
				daemon.setAdress(bindTo, port, allowedHosts, false);
				daemon.setReadOnly(ro.length() > 0);
				daemon.start();
			} else {
				errors.add("Unknown command.");
			}
		}
		makePage(ctx, errors);
	}

	private void makePage(ToadletContext ctx, List<String> errors) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Admin the git server", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, path(), null, null));
			errors.clear();
		}

		makeCommonBox(contentNode);
		makeSimpleBox(contentNode);
		makeSSHBox(contentNode);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeCommonBox(HTMLNode parent) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Common settings", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "commonForm");
		boxForm.addChild("#", "GitPlugin uses a local cache to serve from. Congratulation, you have found the reference point for 'backup'.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Cache dir: \u00a0 ");
		boxForm.addChild("input", new String[] { "type", "name", "size", "value", "disabled"}, new String[] { "text", PARAM_CACHEDIR, "50", daemon.getCacheDir(), "disabled" });
		boxForm.addChild("br");
		boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_APPLY, "Apply", "disabled" });
	}

	private void makeSimpleBox(HTMLNode parent) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Simple git server (git://)", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "simpleServerForm");
		boxForm.addChild("#", "This is the anonymous git server. No authentication/encryption/accesscontrol at all. Be carefully.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Port: \u00a0 ");
		boxForm.addChild("input", new String[] { "type", "name", "size", "value"}, new String[] { "text", PARAM_PORT, "7", "9418" });
		boxForm.addChild("br");
		boxForm.addChild("#", "Bind to: \u00a0 ");
		boxForm.addChild("input", new String[] { "type", "name", "size", "value"}, new String[] { "text", PARAM_BINDTO, "20", "127.0.0.1" });
		boxForm.addChild("br");
		boxForm.addChild("#", "Allowed hosts: \u00a0 ");
		boxForm.addChild("input", new String[] { "type", "name", "size", "value"}, new String[] { "text", PARAM_ALLOWEDHOSTS, "20", "127.0.0.1" });
		boxForm.addChild("br");
		if (daemon.isReadOnly()) {
			boxForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", PARAM_READONLY, "ok", "checked" });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", PARAM_READONLY, "ok" });
		}
		boxForm.addChild("#", "\u00a0Read only access");
		boxForm.addChild("br");
		boxForm.addChild("br");

		if (daemon.isRunning()) {
			boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_START, "Start", "disabled" });
			boxForm.addChild("#", "\u00a0 ");
			boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_STOP, "Stop" });
			boxForm.addChild("#", "\u00a0 ");
			boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_RESTART, "Restart" });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_START, "Start"});
			boxForm.addChild("#", "\u00a0 ");
			boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_STOP, "Stop", "disabled" });
			boxForm.addChild("#", "\u00a0 ");
			boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_RESTART, "Restart", "disabled" });
		}
	}

	private void makeSSHBox(HTMLNode parent) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "SSH git server (git+ssh://)", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "sshServerForm");
		boxForm.addChild("#", "Setting up SSH for an 'Just an idea' implementation is far to much. Sorry folks.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Basic code for SSH (jSch) is in, just send patches ;)");
	}
}
