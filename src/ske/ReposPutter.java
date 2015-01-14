package ske;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.ObjectDirectory;
import org.eclipse.jgit.storage.file.PackFile;
import org.eclipse.jgit.transport.RefAdvertiser;

import freenet.client.InsertContext;
import freenet.client.async.BaseManifestPutter;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.TooManyFilesInsertException;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.api.Bucket;
import freenet.support.api.ManifestElement;
import freenet.support.api.RandomAccessBucket;
import freenet.support.io.ArrayBucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.TempBucketFactory;

public class ReposPutter extends BaseManifestPutter {

	public ReposPutter(ClientPutCallback cb,
			HashMap<String, FreenetURI> packList, Repository db, short prioClass,
			FreenetURI target, String defaultName, InsertContext ctx,
			boolean getCHKOnly2, ClientContext clientContext,
			boolean earlyEncode, TempBucketFactory tempBucketFactory) throws TooManyFilesInsertException {
		super(cb, paramTrick(packList, db, tempBucketFactory), prioClass, target, defaultName, ctx, false, (byte[])null, clientContext);
	}

	private static HashMap<String, Object> paramTrick(HashMap<String, FreenetURI> packList, Repository db, TempBucketFactory tbf) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("tbf", tbf);
		result.put("db", db);
		result.put("packList", packList);
		return result;
	}

	@Override
	protected void makePutHandlers(HashMap<String, Object> manifestElements,
			String defaultName) {
		TempBucketFactory tbf = (TempBucketFactory) manifestElements.get("tbf");
		Repository db = (Repository) manifestElements.get("db");
		@SuppressWarnings("unchecked")
		HashMap<String, FreenetURI> packList = (HashMap<String, FreenetURI>) manifestElements.get("packList");

		// make the default page
		String defaultText = "This is a git repository.";
		RandomAccessBucket b = new ArrayBucket(defaultText.getBytes());
		ManifestElement defaultItem = new ManifestElement("defaultText", b, "text/plain", b.size());
		ContainerBuilder container = getRootContainer();
		container.addItem("defaultText", defaultItem, true);

		// generate info files for dumb servers (fproxy)
		container.pushCurrentDir();
		container.pushCurrentDir();
		try {
			// info/refs
			RandomAccessBucket refs = generateInfoRefs(db, tbf);
			ManifestElement refsItem = new ManifestElement("refs", refs, "text/plain", refs.size());
			container.makeSubDirCD("info");
			container.addItem("refs", refsItem, false);
			container.popCurrentDir();

			// objects/info/packs
			RandomAccessBucket packs = generateObjectsInfoPacks(db, tbf);
			ManifestElement packsItem = new ManifestElement("packs", packs, "text/plain", packs.size());
			container.makeSubDirCD("objects");
			container.makeSubDirCD("info");
			container.addItem("packs", packsItem, false);
			container.popCurrentDir();

			parseDir(db.getDirectory(), container, tbf, false, packList);
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error(e);
		}
	}

	private RandomAccessBucket generateInfoRefs(Repository db, TempBucketFactory tbf) throws IOException {
		RandomAccessBucket result = tbf.makeBucket(-1);
		final RevWalk walk = new RevWalk(db);
		final RevFlag ADVERTISED = walk.newFlag("ADVERTISED");

		final OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(result.getOutputStream()), Constants.CHARSET);

		final RefAdvertiser adv = new RefAdvertiser() {
			@Override
			protected void writeOne(final CharSequence line) throws IOException {
				// Whoever decided that info/refs should use a different
				// delimiter than the native git:// protocol shouldn't
				// be allowed to design this sort of stuff. :-(
				out.append(line.toString().replace(' ', '\t'));
			}

			@Override
			protected void end() {
				// No end marker required for info/refs format.
			}
		};
		adv.init(walk, ADVERTISED);
		adv.setDerefTags(true);

		Map<String, Ref> refs = db.getAllRefs();
		refs.remove(Constants.HEAD);
		adv.send(refs);
		out.close();
		result.setReadOnly();
		return result;
	}

	private RandomAccessBucket generateObjectsInfoPacks(Repository db, TempBucketFactory tbf) throws IOException {
		RandomAccessBucket result = tbf.makeBucket(-1);
		
		final OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(result.getOutputStream()), Constants.CHARSET);
		final ObjectDatabase ob = db.getObjectDatabase();
		if (ob instanceof ObjectDirectory) {
			for (PackFile pack : ((ObjectDirectory) ob).getPacks()) {
				out.append("P ");
				out.append(pack.getPackFile().getName());
				out.append('\n');
			}
		}
		out.append('\n');
		out.close();
		result.setReadOnly();
		return result;
	}

	private void parseDir(File dir, ContainerBuilder container, TempBucketFactory tbf, boolean checkPack, HashMap<String, FreenetURI> packList) throws IOException {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				container.pushCurrentDir();
				container.makeSubDirCD(file.getName());
				if (checkPack && file.getName().equals("pack")) {
					parsePackList(file, packList, container, tbf);
				} else {
					parseDir(file, container, tbf, ((packList != null) && file.getName().equals("objects")), packList);
				}
				container.popCurrentDir();
			} else {
				addFile(file, container, tbf);
			}
		}
	}

	private void parsePackList(File dir, HashMap<String, FreenetURI> packList, ContainerBuilder container, TempBucketFactory tbf) throws IOException {
		File[] files = dir.listFiles();
		for (File file : files) {
			String name = file.getName();
			if (packList.containsKey(name)) {
				FreenetURI target = packList.get(name);
				ManifestElement me = new ManifestElement(file.getName(), target, "text/plain");
				container.addItem(file.getName(), me, false);
			} else {
				addFile(file, container, tbf);
			}
		}
	}

	private void addFile(File file, ContainerBuilder container, TempBucketFactory tbf) throws IOException {
		RandomAccessBucket b = makeBucket(file, tbf);
		ManifestElement me = new ManifestElement(file.getName(), b, "text/plain", b.size());
		container.addItem(file.getName(), me, false);
	}

	private RandomAccessBucket makeBucket(File file, TempBucketFactory tbf) throws IOException {
		RandomAccessBucket b = tbf.makeBucket(file.length());
		InputStream is = new FileInputStream(file);
		try {
			BucketTools.copyFrom(b, is, Long.MAX_VALUE);
		} catch (IOException e) {
			Closer.close(b);
			throw e;
		} finally {
			Closer.close(is);
		}
		b.setReadOnly();
		return b;
	}
}
