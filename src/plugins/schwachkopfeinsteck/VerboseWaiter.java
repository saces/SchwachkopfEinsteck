package plugins.schwachkopfeinsteck;

import com.db4o.ObjectContainer;

import freenet.client.PutWaiter;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.Logger;

public class VerboseWaiter extends PutWaiter implements ClientEventListener {

	public VerboseWaiter(RequestClient client) {
		super(client);
	}

	@Override
	public void onFetchable(BaseClientPutter state) {
		Logger.error(this, "Put fetchable");
		super.onFetchable(state);
	}

	@Override
	public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state) {
		Logger.error(this, "Got UriGenerated: "+uri.toString(false, false));
		super.onGeneratedURI(uri, state);
	}

	public void onRemoveEventProducer(ObjectContainer container) {
		Logger.error(this, "TODO", new Exception("TODO"));
	}

	public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
		Logger.error(this, "Progress: "+ce.getDescription());
	}

	@Override
	public void receive(ClientEvent ce, ClientContext context) {
		// TODO Auto-generated method stub
		Logger.error(this, "TODO ClientEvent :"+ce.getDescription(), new Exception("TODO"));
	}
}

