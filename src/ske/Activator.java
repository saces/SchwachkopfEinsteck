package ske;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

import freenet.client.HighLevelSimpleClient;

public class Activator implements BundleActivator {

	HttpService service;

	public void start(BundleContext context) throws Exception {
		System.out.println("Hello SKE!");

		// first check the http service. really useless without.
		ServiceReference reference1 = context.getServiceReference(HttpService.class.getName());
		service = (HttpService) context.getService(reference1);
		HttpContext httpContext = service.createDefaultHttpContext();

		// check the http service. really useless without.
		ServiceReference reference2 = context.getServiceReference(HighLevelSimpleClient.class.getName());
		HighLevelSimpleClient hlsc = (HighLevelSimpleClient) context.getService(reference2);

		// check the chache dir
		final File cacheDir = Utils.ensureCacheDirExists("./gitcache");

		Dictionary initparams = new Hashtable(); //context.getBundle().getHeaders();
		//initparams.put("base-path", new File("./gitcache").getAbsolutePath());
		//initparams.put("export-all", "true");
		GitServlet gs = new GitServlet();
		RepositoryResolver rr = new SimpleReposResolver(new File("./gitcache").getAbsoluteFile());
		SimpleRecivepackFactory rpf = new SimpleRecivepackFactory(hlsc);
		gs.setRepositoryResolver(rr);
		gs.setReceivePackFactory(rpf);
		service.registerServlet("/git", gs, initparams, httpContext);
	}

	public void stop(BundleContext context) throws Exception {
		// TODO Auto-generated method stub
		System.out.println("Bye SKE!");
		service.unregister("/git");
	}

}
