package com.maxzxwd.msb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.resource.Resource;

public class WebSocketServer extends Server {
  private static final Type LIST_TYPE = new TypeToken<ArrayList<String>>(){}.getType();
  public static final Gson GSON;
  public static final File JAR_DIR;
  public static final File RESOURCES_DIR;
  public static final Settings SETTINGS;
  public static final List<String> IP_BANS;

  static {
    GSON = new GsonBuilder().setPrettyPrinting().create();

    File jarDir = null;
    try {
      jarDir = new File(WebSocketServer.class.getProtectionDomain().getCodeSource()
          .getLocation().toURI().getPath()).getParentFile();

    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    JAR_DIR = jarDir;

    Settings settings;
    try {
      settings = readSettings();
    } catch (IOException e) {
      e.printStackTrace();
      settings = new Settings();
    }
    SETTINGS = settings;

    List<String> ipBans;
    try {
      ipBans = readIpBans();
    } catch (IOException e) {
      e.printStackTrace();
      ipBans = new ArrayList<>();
    }
    IP_BANS = ipBans;

    RESOURCES_DIR = new File(JAR_DIR, SETTINGS.resourcesDirName);
    RESOURCES_DIR.mkdirs();
  }

  public static void main(String... args) throws Exception {
    WebSocketServer server = new WebSocketServer();

    ServerConnector connector = new ServerConnector(server);
    connector.setPort(SETTINGS.port);
    server.addConnector(connector);

    FilterHolder filterHolder = new FilterHolder(CrossOriginFilter.class);
    filterHolder.setInitParameter("allowedOrigins", "*");
    filterHolder.setInitParameter("allowedMethods", "GET,POST,DELETE,PUT,HEAD,OPTIONS");
    filterHolder.setInitParameter("supportsCredentials", "true");
    filterHolder.setInitParameter("chainPreflight", "false");
    filterHolder.setInitParameter("allowedHeaders", "origin, content-type, cache-control, accept, options, authorization, x-requested-with");

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath(SETTINGS.contextPath);
    handler.setBaseResource(Resource.newResource(RESOURCES_DIR));
    server.setHandler(handler);

    handler.addServlet(MessagingServlet.class, "/game");
    handler.addServlet(DefaultServlet.class, "/");
    handler.addFilter(filterHolder, "/*", null);

    server.start();
    server.dump(System.err);

    Scanner sn = new Scanner(System.in);
    while (server.isRunning()) {
      String nextLine = sn.nextLine();
      if (nextLine.equalsIgnoreCase(SETTINGS.stopCommand)) {
        break;
      } else {
        if (nextLine.equalsIgnoreCase(SETTINGS.ipbanCommand)) {
          String[] splitted = nextLine.split(" ");
          if (splitted.length <= 1) {
            System.out.println("Usage: " + SETTINGS.ipbanCommand + " [ip]");
          } else {
            banIp(splitted[1]);
            System.out.println("Ip " + splitted[1] + " was banned");
          }
        }
        Thread.sleep(100);
      }
    }
    System.exit(0);
  }

  private static void banIp(String ip) {
    List<String> tempBans;
    try {
      tempBans = readIpBans();
    } catch (IOException ignore) {
      tempBans = IP_BANS;
    }
    tempBans.add(ip);

    IP_BANS.clear();
    IP_BANS.addAll(tempBans);
    try {
      File file = new File(JAR_DIR, SETTINGS.ipBansFileName);
      file.createNewFile();
      Files.write(file.toPath(), GSON.toJson(IP_BANS, LIST_TYPE)
              .getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException ignore) {
    }
  }

  private static List<String> readIpBans() throws IOException {

    File file = new File(JAR_DIR, SETTINGS.ipBansFileName);

    if (!file.exists()) {
      file.createNewFile();
      List<String> ipBans = new ArrayList<>();
      Files.write(file.toPath(), GSON.toJson(ipBans, LIST_TYPE)
              .getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return ipBans;
    }
    return GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
        LIST_TYPE);
  }

  private static Settings readSettings() throws IOException {
    File file = new File(JAR_DIR, "settings.json");

    if (!file.exists()) {
      file.createNewFile();
      Settings settings = new Settings();
      Files.write(file.toPath(), GSON.toJson(settings, Settings.class)
              .getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return settings;
    }
    return GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
        Settings.class);
  }
}
