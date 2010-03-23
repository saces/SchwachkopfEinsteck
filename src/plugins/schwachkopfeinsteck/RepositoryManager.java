package plugins.schwachkopfeinsteck;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.lib.Repository;

import com.db4o.ObjectContainer;

import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.ClientRequester;
import freenet.client.async.DatabaseDisabledException;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.Logger;
import freenet.support.io.FileUtil;
import freenet.support.plugins.helpers1.PluginContext;

public class RepositoryManager {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(GitPlugin.class);
	}

	public static class RepositoryWrapper {
		public final String name;
		public final Repository db;
		public final ReentrantReadWriteLock rwLock;

		RepositoryWrapper(String name2, Repository db2, ReentrantReadWriteLock rwLock2) {
			name = name2;
			db = db2;
			rwLock = rwLock2;
		}
	}

	private final File cacheDir;
	private final WeakHashMap<String, ReentrantReadWriteLock> locks = new WeakHashMap<String, ReentrantReadWriteLock>();
	private final WeakHashMap<String, Repository> dbCache = new WeakHashMap<String, Repository>();
	private final HashMap<String, ClientRequester> jobs = new HashMap<String, ClientRequester>();

	RepositoryManager(File cachedir) {
		cacheDir = cachedir;
	}

	private ReentrantReadWriteLock getRRWLock(String name) {
		ReentrantReadWriteLock result;
		synchronized(locks) {
			result = locks.get(name);
			if (result == null) {
				result = new ReentrantReadWriteLock(true);
				locks.put(name, result);
			}
		}
		return result;
	}

	private Repository internalGetRepository(String name) throws IOException {
		Repository result;
		synchronized(dbCache) {
			result = dbCache.get(name);
			if (result == null) {
				File reposDir = new File(cacheDir, name);
				if (!reposDir.exists()) {
					return null;
				}
				result = new Repository(reposDir);
				dbCache.put(name, result);
			}
		}
		return result;
	}

	public RepositoryWrapper getRepository(String name) throws IOException {
		Repository db = internalGetRepository(name);
		if (db == null) {
			return null;
		}
		ReentrantReadWriteLock lock = getRRWLock(name);
		return new RepositoryWrapper(name, db, lock);
	}

	public static File ensureCacheDirExists(String dir) throws IOException {
		File newDir = new File(dir);
		if (newDir.exists()) {
			if (!newDir.isDirectory()) {
				throw new IOException("Not a directory: "+newDir.getAbsolutePath());
			}
			return newDir;
		}
		if (newDir.mkdirs()) {
			return newDir;
		}
		throw new IOException("Unable to create cache directory: "+newDir.getAbsolutePath());
	}

	/**
	 * get the internal repository name from freenet uri.
	 * must be the request uri.
	 */
	public static String getRepositoryName(FreenetURI uri) {
		String docName = uri.getDocName();
		uri = uri.setKeyType("SSK");
		String reposName = uri.setDocName(null).setMetaString(null).toString(false, false);
		return new String(reposName + '@' + docName);
	}

	public String getCacheDir() {
		return cacheDir.getPath();
	}

	public void deleteRepository(String reposName) {
		ReentrantReadWriteLock lock = getRRWLock(reposName);
		synchronized (lock) {
			File repos = new File(cacheDir, reposName);
			FileUtil.removeAll(repos);
		}
	}

	public void updateDescription(String repos, String desc) throws IOException {
		File reposFile = new File(cacheDir, repos);
		updateDescription(reposFile, desc);
	}

	private void updateDescription(File repos, String desc) throws IOException {
		ReentrantReadWriteLock lock = getRRWLock(repos.getName());
		synchronized (lock) {
			File descfile = new File(repos, "description");
			if (descfile.exists()) {
				descfile.delete();
			}
			InputStream is = new ByteArrayInputStream(desc.getBytes("UTF-8"));
			FileUtil.writeTo(is, descfile);
		}
	}

	private void updateEditionHint(File repos, long edition) throws IOException {
		ReentrantReadWriteLock lock = getRRWLock(repos.getName());
		synchronized (lock) {
			File descfile = new File(repos, "EditionHint");
			if (descfile.exists()) {
				descfile.delete();
			}
			InputStream is = new ByteArrayInputStream(Long.toString(edition).getBytes("UTF-8"));
			FileUtil.writeTo(is, descfile);
		}
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

	public FreenetURI insert(RepositoryWrapper rw, FreenetURI fetchURI, FreenetURI insertURI, PluginContext pluginContext) throws InsertException {
		RequestClient rc = new RequestClient() {
			public boolean persistent() {
				return false;
			}
			public void removeFrom(ObjectContainer container) {
			}
			
		};
		InsertContext iCtx = pluginContext.hlsc.getInsertContext(true);
		iCtx.compressorDescriptor = "LZMA";
		VerboseWaiter pw = new VerboseWaiter();
		File reposDir = new File(cacheDir, rw.name);
		ReposInserter1 dmp = new ReposInserter1(pw, reposDir, rw.db, (short) 1, insertURI.setMetaString(null), "index.html", iCtx, false, rc, false, pluginContext.clientCore.tempBucketFactory);
		iCtx.eventProducer.addEventListener(pw);
		try {
			pluginContext.clientCore.clientContext.start(dmp);
		} catch (DatabaseDisabledException e) {
			// Impossible
		}
		FreenetURI result;
		try {
			result = pw.waitForCompletion();
		} finally {
			iCtx.eventProducer.removeEventListener(pw);
		}
		try {
			updateEditionHint(reposDir, result.getEdition());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
}
