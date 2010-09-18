package ske;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;
import org.eclipse.jgit.http.server.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.http.server.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FS;

import freenet.keys.FreenetURI;
import freenet.keys.InsertableUSK;

public class SimpleReposResolver implements RepositoryResolver {

	private final File basePath;

	public SimpleReposResolver(final File basePath) {
		this.basePath = basePath;
	}

	public Repository open(HttpServletRequest req, String name) 
		throws RepositoryNotFoundException, ServiceNotAuthorizedException,
		ServiceNotEnabledException {
			try {
				return internalOpen(req, name);
			} catch (RepositoryNotFoundException e) {
				// TODO Auto-generated catch block
				System.err.println("ERR: "+e.getMessage());
				e.printStackTrace();
				throw e;
			} catch (ServiceNotAuthorizedException e) {
				// TODO Auto-generated catch block
				System.err.println("ERR: "+e.getMessage());
				e.printStackTrace();
				throw e;
			} catch (ServiceNotEnabledException e) {
				// TODO Auto-generated catch block
				System.err.println("ERR: "+e.getMessage());
				e.printStackTrace();
				throw e;
			}
	}

	public Repository internalOpen(HttpServletRequest req, String name)
			throws RepositoryNotFoundException, ServiceNotAuthorizedException,
			ServiceNotEnabledException {
		System.out.println("Resolve repos '"+name+"'.");

		// reposName is the uri
		FreenetURI iUri = null;
		FreenetURI rUri;
		try {
			rUri = new FreenetURI(name);
		} catch (MalformedURLException mue) {
			throw new RepositoryNotFoundException("Not a valid Freenet URI!", mue);
		}
		if (!rUri.isUSK()) {
			throw new RepositoryNotFoundException("Repository uri must be an USK.");
		}
		// if it is an insert uri, get the request uri from it.
		if(rUri.getExtra()[1] == 1) {
			iUri = rUri;
			InsertableUSK iUsk;
			try {
				iUsk = InsertableUSK.createInsertable(rUri, false);
			} catch (MalformedURLException mue) {
				throw new RepositoryNotFoundException("Internal failure", mue);
			}
			rUri = iUsk.getURI();
		}

		// reposName is the internal repository name
		String reposName = Utils.getRepositoryName(rUri);
		final Repository db;
		try {
			final File gitdir = new File(basePath, reposName);
			if (!gitdir.exists()) {
				db = new FileRepository(gitdir);
				db.create(true);
				RepositoryCache.register(db);
			} else {
				db = RepositoryCache.open(FileKey.exact(gitdir, FS.DETECTED), true);
			}
		} catch (IOException e) {
			throw new RepositoryNotFoundException(reposName, e);
		}

		if (iUri != null) {
			req.setAttribute("FETCH_URI", rUri);
			req.setAttribute("INSERT_URI", iUri);
		}

		return db;
	}

}
