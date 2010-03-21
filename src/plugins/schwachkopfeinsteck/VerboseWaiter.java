package plugins.schwachkopfeinsteck;

import com.db4o.ObjectContainer;

import freenet.client.PutWaiter;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

public class VerboseWaiter extends PutWaiter implements ClientEventListener {

	public VerboseWaiter() {
		super();
	}

	@Override
	public void onFetchable(BaseClientPutter state, ObjectContainer container) {
		Logger.error(this, "Put fetchable");
		super.onFetchable(state, container);
	}

	@Override
	public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) {
		Logger.error(this, "Got UriGenerated: "+uri.toString(false, false));
		super.onGeneratedURI(uri, state, container);
	}

	public void onRemoveEventProducer(ObjectContainer container) {
		Logger.error(this, "TODO", new Exception("TODO"));
	}

	public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
		Logger.error(this, "Progress: "+ce.getDescription());
	}
}

