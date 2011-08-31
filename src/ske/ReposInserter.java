package ske;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map.Entry;

import org.eclipse.jgit.lib.Repository;

import plugins.schwachkopfeinsteck.VerboseWaiter;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.Metadata;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.DatabaseDisabledException;
import freenet.client.async.SnoopMetadata;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Fields;
import freenet.support.Logger;
import freenet.support.io.FileUtil;

public class ReposInserter {

	public static FreenetURI insert(Repository repos, FreenetURI fetchURI, FreenetURI insertURI, HighLevelSimpleClient hlsc) throws InsertException {

		//get the edition hint
		long hint = getEditionHint(repos.getDirectory());
		HashMap<String, FreenetURI> packList = null;
		if (hint > -1) {
			// it seems the repository was inserted before, try to fetch the manifest to reuse the pack files
			FreenetURI u = fetchURI.setSuggestedEdition(hint).sskForUSK();
			packList = getPackList(hlsc, u);
		}

		RequestClient rc = new RequestClient() {
			public boolean persistent() {
				return false;
			}
			public boolean realTimeFlag() {
				return true;
			}
			public void removeFrom(ObjectContainer container) {
			}
			
		};
		InsertContext iCtx = hlsc.getInsertContext(true);
		iCtx.compressorDescriptor = "LZMA";
		VerboseWaiter pw = new VerboseWaiter();
		ReposPutter dmp = new ReposPutter(pw, packList, repos, (short) 1, insertURI.setMetaString(null), "index.html", iCtx, false, rc, false, hlsc.getTempBucketFactory());
		iCtx.eventProducer.addEventListener(pw);
		try {
			hlsc.startPutter(dmp);
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
			updateEditionHint(repos.getDirectory(), result.getEdition());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	private static long getEditionHint(File repos) {
		//ReentrantReadWriteLock lock = getRRWLock(repos.getName());
		String hint;
		//synchronized (lock) {
			File hintfile = new File(repos, "EditionHint");
			if (!hintfile.exists()) {
				return -1;
			}
			try {
				hint = FileUtil.readUTF(hintfile);
			} catch (IOException e) {
				Logger.error(ReposInserter.class, "Failed to read EditionHint for: "+repos.getName()+". Forcing full upload.");
				return -1;
			}
		//}
		return Fields.parseLong(hint, -1);
	}

	public static class Snooper implements SnoopMetadata {
		private Metadata metaData;

		Snooper() {
		}

		public boolean snoopMetadata(Metadata meta, ObjectContainer container, ClientContext context) {
			if (meta.isSimpleManifest()) {
				metaData = meta;
				return true;
			}
			return false;
		}
	}

	// get the fragment 'pack files list' from metadata, expect a ssk
	private static HashMap<String, FreenetURI> getPackList(HighLevelSimpleClient hlsc, FreenetURI uri) {
		// get the list for reusing pack files
		Snooper snooper = new Snooper();
		FetchContext context = hlsc.getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, uri.setMetaString(new String[]{"fake"}), context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)hlsc, null, null);
		get.setMetaSnoop(snooper);
		try {
			hlsc.startGetter(get);
			fw.waitForCompletion();
		} catch (FetchException e) {
			Logger.error(ReposInserter.class, "Fetch failure.", e);
		} catch (DatabaseDisabledException e) {
			// impossible
			e.printStackTrace();
		}

		if (snooper.metaData == null) {
			// nope. force a full insert
			return null;
		}
		HashMap<String, Metadata> list;
		try {
			// FIXME deal with MultiLevelMetadata, the pack dir can get huge
			list = snooper.metaData.getDocument("objects").getDocument("pack").getDocuments();
		} catch (Throwable t) {
			Logger.error(ReposInserter.class, "Error transforming metadata, really a git repository? Or a Bug/MissingFeature.", t);
			return null;
		}
		HashMap<String, FreenetURI> result = new HashMap<String, FreenetURI>();
		for (Entry<String, Metadata> e:list.entrySet()) {
			String n = e.getKey();
			Metadata m = e.getValue();
			if (m.isSingleFileRedirect()) {
				// already a redirect, reuse it
				FreenetURI u = m.getSingleTarget();
				result.put(n, u);
			} else {
				FreenetURI u = uri.setMetaString(new String[]{"objects", "pack", n});
				result.put(n, u);
			}
		}
		return result;
	}

	private static void updateEditionHint(File repos, long edition) throws IOException {
		//ReentrantReadWriteLock lock = getRRWLock(repos.getName());
		//synchronized (lock) {
			File descfile = new File(repos, "EditionHint");
			if (descfile.exists()) {
				descfile.delete();
			}
			InputStream is = new ByteArrayInputStream(Long.toString(edition).getBytes("UTF-8"));
			FileUtil.writeTo(is, descfile);
		//}
	}
}
