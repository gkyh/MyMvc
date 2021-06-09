package cn.cgywin.framework.servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
/*
 * 
		<!-- https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-servlet -->
		<dependency>
		    <groupId>org.eclipse.jetty</groupId>
		    <artifactId>jetty-servlet</artifactId>
		    <version>9.2.0.RC0</version>
		</dependency>
 */

public class JettyServer {

	private void start() throws Exception {

        int port = 6666;

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        DisptcherServlet dh=new DisptcherServlet();
        context.addServlet(new ServletHolder(dh), "/*");
        dh.InitConfig();
        port = dh.getPort();

        Server server = new Server(port);
        server.setHandler(context);
        //ServletHolder sh = new ServletHolder(DisptcherServlet.class);
        //ServletHolder sh = new ServletHolder(ServletContainer.class);
        //sh.setInitParameter("com.sun.jersey.config.property.resourceConfigClass",
        //        "com.sun.jersey.api.core.PackagesResourceConfig");
        //sh.setInitParameter("cn.cgywin.framework.servlet.DisptcherServlet","cn.cgywin.framework.servlet");
          
        //context.addServlet(sh, "/*");
        
        server.start();
    }

    public void stop() throws Exception {

    }

    public static void main(String[] args) throws Exception {
        JettyServer server = new JettyServer();
        server.start();
    }

}
