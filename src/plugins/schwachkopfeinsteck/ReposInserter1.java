package plugins.schwachkopfeinsteck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import com.db4o.ObjectContainer;

import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseManifestPutter;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.DatabaseDisabledException;
import freenet.client.async.ManifestElement;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.api.Bucket;
import freenet.support.io.ArrayBucket;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.TempBucketFactory;
import freenet.support.plugins.helpers1.PluginContext;

public class ReposInserter1 extends BaseManifestPutter {

	public ReposInserter1(ClientPutCallback cb,
			File reposDir, short prioClass,
			FreenetURI target, String defaultName, InsertContext ctx,
			boolean getCHKOnly2, RequestClient clientContext,
			boolean earlyEncode, TempBucketFactory tempBucketFactory) {
		super(cb, paramTrick(reposDir, tempBucketFactory), prioClass, target, defaultName, ctx, getCHKOnly2,
				clientContext, earlyEncode);
	}

	private static HashMap<String, Object> paramTrick(File reposDir, TempBucketFactory tbf) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("rDir", reposDir);
		result.put("tbf", tbf);
		return result;
	}

	@Override
	protected void makePutHandlers(HashMap<String, Object> manifestElements,
			String defaultName) {
		File reposDir = (File) manifestElements.get("rDir");
		TempBucketFactory tbf = (TempBucketFactory) manifestElements.get("tbf");
		String defaultText = "This is a git repository.";
		Bucket b = new ArrayBucket(defaultText.getBytes());
		ManifestElement defaultItem = new ManifestElement("defaultText", b, "text/plain", b.size());
		freenet.client.async.BaseManifestPutter.ContainerBuilder container = getRootContainer();
		container.addItem("defaultText", defaultItem, true);
		try {
			parseDir(reposDir, container, tbf);
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error(e);
		}
	}

	private void parseDir(File dir, ContainerBuilder container, TempBucketFactory tbf) throws IOException {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				container.pushCurrentDir();
				container.makeSubDirCD(file.getName());
				parseDir(file, container, tbf);
				container.popCurrentDir();
			} else {
				addFile(file, container, tbf);
			}
		}
	}

	private void addFile(File file, ContainerBuilder container, TempBucketFactory tbf) throws IOException {
		Bucket b = makeBucket(file, tbf);
		ManifestElement me = new ManifestElement(file.getName(), b, "text/plain", b.size());
		container.addItem(file.getName(), me, false);
	}

	private Bucket makeBucket(File file, TempBucketFactory tbf) throws IOException {
		Bucket b = tbf.makeBucket(file.length());
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

	public static FreenetURI insert(File reposDir, FreenetURI insertURI, PluginContext pluginContext) throws InsertException {
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
		ReposInserter1 dmp = new ReposInserter1(pw, reposDir, (short) 1, insertURI.setMetaString(null), "index.html", iCtx, false, rc, false, pluginContext.clientCore.tempBucketFactory);
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
		return result;
	}
	

}
