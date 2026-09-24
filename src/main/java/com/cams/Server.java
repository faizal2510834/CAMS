package com.cams;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

/**
 * Embedded Apache Tomcat 10 launcher for CAMS.
 * Serves the web application from src/main/webapp and classes from target/classes.
 */
public class Server {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            port = Integer.parseInt(portEnv.trim());
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir("target/tomcat");

        // Force connector initialization
        tomcat.getConnector();

        String webappDir = new File("src/main/webapp").getAbsolutePath();
        Context ctx = tomcat.addWebapp("", webappDir);
        ctx.setParentClassLoader(Server.class.getClassLoader());

        // Configure classloader to see target/classes (servlets, services, DAOs, models, db.properties)
        File classesDir = new File("target/classes");
        if (classesDir.exists()) {
            StandardRoot resources = new StandardRoot(ctx);
            resources.addPreResources(new DirResourceSet(
                    resources, "/WEB-INF/classes", classesDir.getAbsolutePath(), "/"
            ));
            ctx.setResources(resources);
        }

        System.out.println("========================================================");
        System.out.println("  CAMS - Campus Asset Management System");
        System.out.println("  Embedded Apache Tomcat 10 running on:");
        System.out.println("  --> http://localhost:" + port);
        System.out.println("  --> Health API: http://localhost:" + port + "/api/ping");
        System.out.println("========================================================");

        tomcat.start();
        tomcat.getServer().await();
    }
}
