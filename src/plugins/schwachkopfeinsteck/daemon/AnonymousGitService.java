package plugins.schwachkopfeinsteck.daemon;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import plugins.schwachkopfeinsteck.RepositoryManager;
import plugins.schwachkopfeinsteck.RepositoryManager.RepositoryWrapper;

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
	private final RepositoryManager repositoryManager;
	
	private static final long LOCK_TIMEOUT = 5;
	private static final TimeUnit LOCK_TIMEUNIT = TimeUnit.SECONDS;
	

	public AnonymousGitService(boolean readOnly, Executor executor, RepositoryManager repositorymanager) {
		isReadOnly = readOnly;
		eXecutor = executor;
		repositoryManager = repositorymanager;
	}

	public void handle(Socket sock) throws IOException {
		InputStream rawIn = new BufferedInputStream(sock.getInputStream());
		OutputStream rawOut = new BufferedOutputStream(sock.getOutputStream());

		String cmd = new PacketLineIn(rawIn).readStringRaw();
		if (logDEBUG) {
			Logger.debug(this, "Incomming request: " + cmd);
		}
		final int nul = cmd.indexOf('\0');
		if (nul >= 0) {
			// Newer clients hide a "host" header behind this byte.
			// Currently we don't use it for anything, so we ignore
			// this portion of the command.
			cmd = cmd.substring(0, nul);
			if (logDEBUG) {
				Logger.debug(this, "Request did contain a hidden host, truncating: " + cmd);
			}
		}

		String req[] = cmd.split(" ");
		if (req.length != 2) {
			Logger.error(this, "Malformed request: " + cmd);
			fatal(rawOut, "Malformed request.");
			return;
		}

		String command = req[0].startsWith("git-") ? req[0] : "git-" + req[0];
		String reposName = req[1];

		// adjust uri string
		if (reposName.startsWith("/")) {
			reposName = reposName.substring(1);
		}

		if (!reposName.endsWith("/")) {
			reposName = reposName + '/';
		}

		// reposName is the uri
		FreenetURI iUri = null;
		FreenetURI rUri;
		try {
			rUri = new FreenetURI(reposName);
		} catch (MalformedURLException e1) {
			fatal(rawOut, "Not a valid Freenet URI: "+e1.getLocalizedMessage());
			return;
		}
		if (!rUri.isUSK()) {
			fatal(rawOut, "Repository uri must be an USK.");
			return;
		}
		// if it is an insert uri, get the request uri from it.
		if(rUri.getExtra()[1] == 1) {
			iUri = rUri;
			InsertableUSK iUsk = InsertableUSK.createInsertable(rUri, false);
			rUri = iUsk.getURI();
		}

		// reposName is the internal repository name
		reposName = RepositoryManager.getRepositoryName(rUri);

		RepositoryWrapper rw = repositoryManager.getRepository(reposName);
		if (rw == null) {
			fatal(rawOut, "No such repository.");
			return;
		}

		try {
			innerHandle(command, rw, rawIn, rawOut, rUri, iUri);
		} catch (InterruptedException e) {
			Logger.error(this, "Interrupted.", e);
			fatal(rawOut, "Interrupted.");
		}
	}

	private class LockWorkerThread extends Thread {

		private volatile boolean recivedDone = false;

		private final RepositoryWrapper rW;
		private final InputStream rawIn;
		private final OutputStream rawOut;
		
		private String error = null;
		private final FreenetURI fetchUri;
		private final FreenetURI insertUri;

		LockWorkerThread(RepositoryWrapper rw, InputStream rawin, OutputStream rawout, FreenetURI fetchuri, FreenetURI inserturi) {
			rW = rw;
			rawIn = rawin;
			rawOut = rawout;
			fetchUri = fetchuri;
			insertUri = inserturi;
		}

		@Override
		public void run() {
			try {
				innerRun();
			} catch (InterruptedException e) {
				Logger.error(this, "Interrupted.", e);
			} catch (IOException e) {
				Logger.error(this, "IO Error.", e);
			}
		}

		private void innerRun() throws InterruptedException, IOException {
			Lock lock = rW.rwLock.writeLock();
			if (lock.tryLock() || lock.tryLock(LOCK_TIMEOUT, LOCK_TIMEUNIT)) {
				boolean sucess = false;
				boolean triggerUpload;
				try {
					triggerUpload = handleGitReceivePack(rW.db, rawIn, rawOut);
					sucess = true;
				} finally {
					if (!sucess) {
						lock.unlock();
					}
					recivedDone();
				}

				if (!triggerUpload) {
					if (logMINOR) Logger.minor(this, "Nothing updated. Do not upload.");
					lock.unlock();
					return;
				}

				// downgrade from write to read lock
				Lock newLock = rW.rwLock.readLock();
				newLock.lock();
				lock.unlock();
				lock = newLock;

				if (logDEBUG) {
					Logger.debug(this, "Do upload.");
				}

				try {
					repositoryManager.insert(rW, fetchUri, insertUri);
				} catch (InsertException e) {
					error = "Insert Failure: "+InsertException.getMessage(e.getMode());
					Logger.error(this, error, e);
				} finally {
					lock.unlock();
				}
			} else {
				error = "Was not able to obtain a write lock within 5 seconds.\nTry again later.";
				recivedDone();
			}
		}

		private synchronized void recivedDone() {
			recivedDone = true;
			notifyAll();
		}

		public synchronized void waitForReciveDone() {
			while(!recivedDone) {
				try {
					wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		}
	}

	private void innerHandle(String command, final RepositoryWrapper rw, InputStream rawIn, OutputStream rawOut, final FreenetURI rUri, final FreenetURI iUri) throws IOException, InterruptedException {
		if ("git-upload-pack".equals(command)) {
			// the client want to have missing objects
			Lock lock = rw.rwLock.readLock();
			if (lock.tryLock() || lock.tryLock(LOCK_TIMEOUT, LOCK_TIMEUNIT)) {
				try {
					handleGitUploadPack(rw.db, rawIn, rawOut);
				} finally {
					lock.unlock();
				}
			} else {
				fatal(rawOut, "Was not able to obtain a read lock within 5 seconds.\nTry again later.");
				return;
			}
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

			LockWorkerThread t = new LockWorkerThread(rw, rawIn, rawOut, rUri, iUri);
			eXecutor.execute(t);

			t.waitForReciveDone();

			if (t.error != null) {
				fatal(rawOut, t.error);
			}
		} else {
			Logger.error(this, "Unknown command: "+command);
			fatal(rawOut, "Unknown command: "+command);
		}
	}

	private void handleGitUploadPack(Repository db, InputStream rawIn, OutputStream rawOut) throws IOException {
		final UploadPack up = new UploadPack(db);
		//up.setTimeout(Daemon.this.getTimeout());
		//up.setPackConfig(getPackConfig());
		up.upload(rawIn, rawOut, null);
	}

	private boolean handleGitReceivePack(final Repository db, InputStream rawIn, OutputStream rawOut) throws IOException {
		final ReceivePack rp = new ReceivePack(db);
		final String name = "anonymous";
		final String email = name + "@freenet";
		rp.setRefLogIdent(new PersonIdent(name, email));
		//rp.setTimeout(Daemon.this.getTimeout());
		rp.receive(rawIn, rawOut, null);
		// TODO figure out hox to check for new objects
		// for now each push does an upload
//		try {
//			rp.getNewObjectIds();
//		} catch ( NullPointerException npe) {
//			return false;
//		}
		return true;
	}

	private void fatal(OutputStream rawOut, String string) throws IOException {
		PacketLineOut pckOut = new PacketLineOut(rawOut);
		byte[] data = ("ERR "+string).getBytes();
		pckOut.writePacket(data);
		pckOut.flush();
		rawOut.flush();
	}

}
