package ske;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.http.server.resolver.ReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.http.server.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;

import freenet.client.HighLevelSimpleClient;
import freenet.keys.FreenetURI;

public class SimpleRecivepackFactory implements ReceivePackFactory {

	final HighLevelSimpleClient _hlsc;

	SimpleRecivepackFactory(HighLevelSimpleClient hlsc) {
		_hlsc = hlsc;
	}
	
	public ReceivePack create(HttpServletRequest req, Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {
		String user = req.getRemoteUser();
		FreenetURI rUri = (FreenetURI) req.getAttribute("FETCH_URI");
		FreenetURI iUri = (FreenetURI) req.getAttribute("INSERT_URI");

		if (user != null && !"".equals(user))
			return createFor(req, db, user, rUri, iUri);
		throw new ServiceNotAuthorizedException();
	}

	private ReceivePack createFor(final HttpServletRequest req,
			final Repository db, final String user, FreenetURI rUri, FreenetURI iUri) {
		final ReceivePack rp = new ReceivePack(db);
		rp.setRefLogIdent(toPersonIdent(req, user));
		rp.setPostReceiveHook(new PostReceiveHaken(_hlsc, rUri, iUri));
		return rp;
	}

	private static PersonIdent toPersonIdent(HttpServletRequest req, String user) {
		return new PersonIdent("anonymose", "anonymose@freenet");
	}

}
