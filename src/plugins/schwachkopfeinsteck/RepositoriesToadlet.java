package plugins.schwachkopfeinsteck;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;

import plugins.schwachkopfeinsteck.daemon.AnonymousGitDaemon;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.node.useralerts.SimpleUserAlert;
import freenet.node.useralerts.UserAlert;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

public class RepositoriesToadlet extends WebInterfaceToadlet {

	private static final String CMD_BROWSE = "browse";
	private static final String CMD_CREATE = "create";
	private static final String CMD_DELETE = "delete";
	private static final String CMD_FORK = "fork";
	private static final String CMD_SAVEDESCRIPTION = "save";

	private static final String PARAM_DELETECONFIRM = "deleteconfirm";
	private static final String PARAM_DESCRIPTION = "description";
	private static final String PARAM_INSERTURI = "inserturi";
	private static final String PARAM_NAME = "name";
	private static final String PARAM_REPOSNAME = "reposuri";
	private static final String PARAM_REQUESTURI = "requesturi";
	private static final String PARAM_SOURCE = "source";
	private static final String PARAM_URI = "uri";

	private static final String URI_WIDTH = "130";
	private final AnonymousGitDaemon daemon;

	// '/', ';', '?' are forbidden to not confuse the URL parser,
	// all others are forbidden to prevent file system clashes
	private static final HashSet<Character> forbiddenChars = new HashSet<Character>(Arrays.asList(
			new Character[] { '/', '\\', '?', '*', ':', ';', '|', '\"', '<', '>'}));

	public RepositoriesToadlet(PluginContext context, AnonymousGitDaemon simpleDaemon) {
		super(context, GitPlugin.PLUGIN_URI, "repos");
		daemon = simpleDaemon;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if (!normalizePath(req.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		List<String> errors = new LinkedList<String>();
		makeMainPage(ctx, errors);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		List<String> errors = new LinkedList<String>();

		if (!isFormPassword(request)) {
			errors.add("Invalid form password");
			makeMainPage(ctx, errors);
			return;
		}

		String path = normalizePath(request.getPath());
		if (request.isPartSet(CMD_SAVEDESCRIPTION)) {
			String repos = request.getPartAsString(PARAM_REPOSNAME, 1024);
			String desc = request.getPartAsString(PARAM_DESCRIPTION, 4096);
			try {
				updateDescription(repos, desc);
			} catch (IOException e) {
				e.printStackTrace();
				errors.add("Error while updating description : "+e.getLocalizedMessage());
			}
		} else if (request.isPartSet(CMD_DELETE)) {
			String repos = request.getPartAsString(PARAM_REPOSNAME, 1024);
			if (request.isPartSet(PARAM_DELETECONFIRM)) {
				deleteRepository(repos);
			} else {
				makeDeleteConfirmPage(ctx, repos);
				return;
			}
		} else if (request.isPartSet(CMD_CREATE)) {
			String name = request.getPartAsString(PARAM_NAME, 1024);
			String iUri = request.getPartAsString(PARAM_INSERTURI, 1024);
			String rUri = request.getPartAsString(PARAM_REQUESTURI, 1024);
			if (name.length() == 0 && rUri.length() == 0 && iUri.length() == 0) {
				errors.add("Are you jokingly? Every field is empty.");
			} else {
				if ((rUri.length() == 0) && (iUri.length() == 0)) {
					FreenetURI[] kp = getClientImpl().generateKeyPair("fake");
					iUri = kp[0].setDocName(null).toString(false, false);
					rUri = kp[1].setDocName(null).toString(false, false);
					errors.add("URI was empty, I generated one for you.");
				}

				name = sanitizeDocName(name, errors);

				// TODO some more uri wodoo
				// a bare fetch uri means import, a bare insert uri get its fetch uri added
				// try to get name from uri if name is not given
				// auto convert ssk
				// etc pp

				if (errors.size() == 0) {
					tryCreateRepository(name, rUri, iUri, errors);
				}
				if (errors.size() > 0) {
					makeCreatePage(ctx, name, rUri, iUri, errors);
					return;
				}
				makeCreatedReposPage(ctx, name, rUri, iUri, errors);
			}
		} else {
			errors.add("Did not understand. Maybe not implemented jet? :P");
		}
		makeMainPage(ctx, errors);
	}

	private void makeMainPage(ToadletContext ctx, List<String> errors) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Admin server repos", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, null, null, null));
			errors.clear();
		}

		makeCreateBox(contentNode, null, null, null);
		makeForkBox(contentNode, null, null, null);
		makeRepositoryBox(contentNode);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeDeleteConfirmPage(ToadletContext ctx, String reposName) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Confirm delete", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		makeDeleteConfirmBox(contentNode, reposName);

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeCreatePage(ToadletContext ctx, String name, String rUri, String iUri, List<String> errors) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Create repository", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (errors.size() > 0) {
			contentNode.addChild(createErrorBox(errors, null, null, null));
			errors.clear();
		}

		makeCreateBox(contentNode, name, rUri, iUri);
		
		contentNode.addChild("br");
		contentNode.addChild("a", "href", path(), "Back to main");

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeCreatedReposPage(ToadletContext ctx, String name, String rUri, String iUri, List<String> errors) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Create repository", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		makeCreatedReposBox(contentNode, name, rUri, iUri);
		
		contentNode.addChild("br");
		contentNode.addChild("a", "href", path(), "Back to main");

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeDeleteConfirmBox(HTMLNode parent, String reposName) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-warning", "Delete a repository", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "deleteConfirmForm");
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", PARAM_REPOSNAME, reposName });
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", PARAM_DELETECONFIRM, PARAM_DELETECONFIRM });
		boxForm.addChild("#", "Confirm deletion of repository:\u00a0");
		boxForm.addChild("b", reposName);
		boxForm.addChild("br");
		boxForm.addChild("a", "href", path(), "Hell, NO!");
		boxForm.addChild("#", "\u00a0");
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_DELETE, "Delete" });
	}

	private void makeCreatedReposBox(HTMLNode parent, String name, String rUri, String iUri) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "\\o/ Repository sucessfully created", parent);
		box.addChild("#", "Created a new repository. Congratz!");
		box.addChild("br");
		box.addChild("#", "Pull URI, give it away if you want:\u00a0 ");
		box.addChild("code", "git://127.0.0.1:9481/U"+rUri.substring(1)+'/'+name+"/0/");
		box.addChild("br");
		box.addChild("br");
		box.addChild("#", "Push URI, Keep it secret! Never give away or share!:\u00a0 ");
		box.addChild("code", "git://127.0.0.1:9481/U"+iUri.substring(1)+'/'+name+"/0/");
		box.addChild("br");
	}

	private void makeCreateBox(HTMLNode parent, String name, String rUri, String iUri) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Create a new repository", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "createForm");
		boxForm.addChild("#", "Create a new repository. Leave the URI fields empty to generate a new one.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Name (will be part of the Freenet URI):\u00a0 ");
		if (name != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_NAME, "30", name });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_NAME, "30"});
		}
		boxForm.addChild("br");
		boxForm.addChild("#", "Request URI:\u00a0 ");
		if (rUri != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_REQUESTURI, URI_WIDTH, rUri });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_REQUESTURI, URI_WIDTH});
		}
		boxForm.addChild("br");
		boxForm.addChild("#", "Insert URI:\u00a0 ");
		if (iUri != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_INSERTURI, URI_WIDTH, iUri });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_INSERTURI, URI_WIDTH });
		}
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_CREATE, "Create" });
	}

	private void makeForkBox(HTMLNode parent, String source, String name, String uri) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Fork a repository", parent);
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(box, path(), "forkForm");
		boxForm.addChild("#", "Fork a repository. Leave the Insert URI empty to generate a new one.");
		boxForm.addChild("br");
		boxForm.addChild("#", "Freenet URI to fork from:\u00a0 ");
		if (source != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_SOURCE, URI_WIDTH, source });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_SOURCE, URI_WIDTH});
		}
		boxForm.addChild("br");
		boxForm.addChild("#", "Name (will be part of the Freenet URI):\u00a0 ");
		if (name != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_NAME, "30", name });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_NAME, "30"});
		}
		boxForm.addChild("br");
		boxForm.addChild("#", "Insert URI:\u00a0 ");
		if (uri != null) {
			boxForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_INSERTURI, URI_WIDTH, uri });
		} else {
			boxForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_INSERTURI, URI_WIDTH });
		}
		boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_FORK, "Fork", "disabled" });
	}

	private void makeRepositoryBox(HTMLNode parent) {
		HTMLNode box = pluginContext.pageMaker.getInfobox("infobox-information", "Repositories", parent);

		File cacheDir = daemon.getCacheDirFile();
		File[] dirs = cacheDir.listFiles();
		if (dirs.length == 0) {
			box.addChild("#", "No repositories set up.");
			return;
		}

		HTMLNode table = box.addChild("table", "width", "100%");
		HTMLNode tableHead = table.addChild("thead");
		HTMLNode tableRow = tableHead.addChild("tr");
		HTMLNode nextTableCell = tableRow.addChild("th", "colspan", "3");
		nextTableCell.addChild("#", "Name");
		nextTableCell = tableRow.addChild("th");
		nextTableCell.addChild("#", "Description");

		for (File dir: dirs) {
			HTMLNode htmlTableRow = table.addChild("tr");
			htmlTableRow.addChild(makeNameCell(dir));
			htmlTableRow.addChild(makeDescriptionCell(dir));

			htmlTableRow = table.addChild("tr");
			htmlTableRow.addChild(makeDeleteCell(dir));
			htmlTableRow.addChild(makeEmptyCell());
			htmlTableRow.addChild(makeForkCell(dir));
		}
	}

	private HTMLNode makeEmptyCell() {
		HTMLNode cell = new HTMLNode("td");
		cell.addChild("#", "\u00a0");
		return cell;
	}

	private HTMLNode makeNameCell(File repos) {
		HTMLNode cell = new HTMLNode("td", "colspan", "3");
		String dirName = repos.getName();
		cell.addChild("b", dirName);
		return cell;
	}

	private HTMLNode makeDescriptionCell(File repos) {
		HTMLNode cell = new HTMLNode("td", "rowspan", "2");
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(cell, path(), "descriptionForm");
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", PARAM_REPOSNAME, repos.getName() });

		HTMLNode area = boxForm.addChild("textarea", new String[]{ "name", "cols", "rows" }, new String[] { PARAM_DESCRIPTION, "70", "4" });
		String desc = getReposDescription(repos);
		if (desc != null) {
			area.addChild("#", desc);
		} else {
			area.addChild("#", "\u00a0");
		}
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_SAVEDESCRIPTION, "Save" });
		return cell;
	}

	private HTMLNode makeDeleteCell(File repos) {
		HTMLNode cell = new HTMLNode("td");
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(cell, path(), "deleteForm");
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", PARAM_REPOSNAME, repos.getName() });
		boxForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_DELETE, "Delete" });
		return cell;
	}

	private HTMLNode makeForkCell(File repos) {
		HTMLNode cell = new HTMLNode("td");
		HTMLNode boxForm = pluginContext.pluginRespirator.addFormChild(cell, path(), "forkForm");
		boxForm.addChild("input", new String[] { "type", "name", "value", "disabled" }, new String[] { "submit", CMD_FORK, "Fork", "disabled" });
		return cell;
	}

	private String getReposDescription(File dir) {
		File descfile = new File(dir, "description");
		if (!descfile.exists()) return "<Describe me!>";
		String desc;
		try {
			desc = FileUtil.readUTF(descfile);
		} catch (IOException e) {
			Logger.error(this, "Error while reading repository description for: "+dir.getAbsolutePath(), e);
			return "Error: "+e.getLocalizedMessage();
		}
		return desc;
	}

	private void updateDescription(String repos, String desc) throws IOException {
		File reposFile = new File(daemon.getCacheDirFile(), repos);
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

	private void deleteRepository(String reposName) {
		File repos = new File(daemon.getCacheDirFile(), reposName);
		FileUtil.removeAll(repos);
	}

	private String getReposURI(File repos) {
		return repos.getName();
	}

	private String getReposURI(String repos) {
		return repos;
	}

	private void tryCreateRepository(String name, String rUri, String iUri, List<String> errors) {
		String dirName = rUri + '@' + name;
		File reposDir = new File(daemon.getCacheDirFile(), dirName);
		Repository repos;
		try {
			repos = new Repository(reposDir);
			repos.create(true);
		} catch (IOException e) {
			Logger.error(this, "Error while create repository: "+reposDir.getAbsolutePath(), e);
			errors.add(e.getLocalizedMessage());
			return;
		}
		String comment = "add a description here";

		String alert = "Due lack of a better idea the URIs are noted here:\n"+
		"Created Repository :"+dirName+"\n"+
		"Pull URI: U"+rUri.substring(1)+'/'+name+"/0/\n"+
		"Push URI: (Keep it secret) U"+iUri.substring(1)+'/'+name+"/0/\n";

		pluginContext.clientCore.alerts.register(new SimpleUserAlert(false, "Repository created", alert, "Repository created", UserAlert.MINOR));

		try {
			updateDescription(reposDir, comment);
		} catch (IOException e) {
			Logger.error(this, "Error while updating repository description for: "+reposDir.getAbsolutePath(), e);
			errors.add(e.getLocalizedMessage());
		}
	}

	private String sanitizeDocName(String docName, List<String> errors) {
		int len = docName.length();
		for (int i=0 ; i<len ; i++) {
			char c = docName.charAt(i);
			if (forbiddenChars.contains(c)) {
				errors.add("The name contains a forbidden character at ("+(i+1)+"): '"+c+"'");
			}
		}
		return docName;
	}
}
