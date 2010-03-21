package plugins.schwachkopfeinsteck.daemon;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import plugins.schwachkopfeinsteck.ReposInserter1;

import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableUSK;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.incubation.server.AbstractService;
import freenet.support.plugins.helpers1.PluginContext;

public class AnonymousGitService implements AbstractService {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(AnonymousGitService.class);
	}

	private final boolean isReadOnly;
	private final Executor eXecutor;
	private final PluginContext pluginContext;

	public AnonymousGitService(boolean readOnly, Executor executor, PluginContext plugincontext) {
		isReadOnly = readOnly;
		eXecutor = executor;
		pluginContext = plugincontext;
	}

	public void handle(Socket sock) throws IOException {
		InputStream rawIn = new BufferedInputStream(sock.getInputStream());
		OutputStream rawOut = new BufferedOutputStream(sock.getOutputStream());

		String cmd = new PacketLineIn(rawIn).readStringRaw();
		final int nul = cmd.indexOf('\0');
		if (nul >= 0) {
			// Newer clients hide a "host" header behind this byte.
			// Currently we don't use it for anything, so we ignore
			// this portion of the command.
			cmd = cmd.substring(0, nul);
		}

		//System.out.println("x händle request:"+cmd);

		String req[] = cmd.split(" ");
		String command = req[0].startsWith("git-") ? req[0] : "git-" + req[0];
		String reposName = req[1];

		// adjust uri string
		if (reposName.startsWith("/")) {
			reposName = reposName.substring(1);
		}

		if (!reposName.endsWith("/")) {
			reposName = reposName + '/';
		}

		// reposname is the uri
		FreenetURI iUri = null;
		FreenetURI rUri = new FreenetURI(reposName);
		if (!rUri.isUSK()) {
			fatal(rawOut, "Repository uri must be an USK");
			return;
		}
		if(rUri.getExtra()[1] == 1) {
			iUri = rUri;
			InsertableUSK iUsk = InsertableUSK.createInsertable(rUri, false);
			rUri = iUsk.getURI();
		}

		//System.out.print("händle:"+command);
		//System.out.println(" for:"+reposName);

		if ("git-upload-pack".equals(command)) {
			// the client want to have missing objects
			Repository db = getRepository(reposName);
			final UploadPack rp = new UploadPack(db);
			//rp.setTimeout(Daemon.this.getTimeout());
			rp.upload(rawIn, rawOut, null);
		} else if ("git-receive-pack".equals(command)) {
			// the client send us new objects
			if (isReadOnly) {
				fatal(rawOut, "Server is read only.");
				return;
			}
			if (iUri == null) {
				fatal(rawOut, "Try an insert uri for push.");
				return;
			}
			Repository db = getRepository(reposName);
			final ReceivePack rp = new ReceivePack(db);
			final String name = "anonymous";
			final String email = name + "@freenet";
			rp.setRefLogIdent(new PersonIdent(name, email));
			//rp.setTimeout(Daemon.this.getTimeout());
			rp.receive(rawIn, rawOut, null);

			final FreenetURI insertURI = iUri;
			final File reposDir = getRepositoryPath(reposName);

			// FIXME
			Process p = Runtime.getRuntime().exec("git update-server-info --force", null, reposDir);
			try {
				p.waitFor();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				// BAH, do not insert
				return;
			}

			// FIXME
			// trigger the upload, the quick&dirty way
			eXecutor.execute(new Runnable (){
				public void run() {
					try {
						ReposInserter1.insert(reposDir, insertURI, pluginContext);
					} catch (InsertException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}});
		} else {
			fatal(rawOut, "Unknown command: "+command);
			System.err.println("Unknown command: "+command);
		}

		//System.out.println("x händle request: pfertsch");

	}

	private File getRepositoryPath(String reposname) throws IOException {
		FreenetURI uri = new FreenetURI(reposname);
		if(uri.getExtra()[1] == 1) {
			InsertableUSK iUsk = InsertableUSK.createInsertable(uri, false);
			uri = iUsk.getURI();
		}

		String docName = uri.getDocName();
		uri = uri.setKeyType("SSK");
		String reposName = uri.setDocName(null).setMetaString(null).toString(false, false);
		return new File("gitcache", reposName + '@' + docName).getCanonicalFile();
	}

	private Repository getRepository(String reposname) throws IOException {
		Repository db;
		File path = getRepositoryPath(reposname);
		db = new Repository(path);
		return db;
	}

	private void fatal(OutputStream rawOut, String string) throws IOException {
		PacketLineOut pckOut = new PacketLineOut(rawOut);
		byte[] data = ("ERR "+string).getBytes();
		pckOut.writePacket(data);
		pckOut.flush();
		rawOut.flush();
	}

}
