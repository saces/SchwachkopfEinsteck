package ske;

import java.util.Collection;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;

public class PostReceiveHaken implements PostReceiveHook {
	
	final HighLevelSimpleClient _hlsc;
	final FreenetURI _fetchURI;
	final FreenetURI _insertURI;

	PostReceiveHaken(HighLevelSimpleClient hlsc, FreenetURI fetchURI, FreenetURI insertURI) {
		_hlsc = hlsc;
		_fetchURI = fetchURI;
		_insertURI = insertURI;
	}
	public void onPostReceive(ReceivePack rp,
			Collection<ReceiveCommand> commands) {
		// TODO Auto-generated method stub
		System.out.println("Haken schlagen hasen.");
		try {
			ReposInserter.insert(rp.getRepository(), _fetchURI, _insertURI, _hlsc);
		} catch (InsertException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
