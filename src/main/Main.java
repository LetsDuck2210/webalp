package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;

import server.HttpStatus;
import server.Server;
import server.Session;
import util.DefaultLogger;
import util.FileUtil;

public class Main {
	private static Logger logger;
	
	public static Logger getLogger() {
		if(logger == null)
			logger = new DefaultLogger("AutologinProxy");
		return logger;
	}

	private static String spAddr;
	private static int spPort;
	
	public static void main(String[] args) throws IOException {
		// -sp <port> <username> <credential>
		if(args.length == 4 && args[0].equals("-sp")) {
			var port = Integer.parseInt(args[1]);
			var username = args[2];
			var credential = args[3];
			
			getLogger().log(Level.INFO, 
					"Hosting session provider on port " + port + "...");
			new SessionProvider(port, username, credential, getLogger()).start();
			
			return;
		}
		if(args.length != 2) {
			getLogger().log(Level.ERROR, 
					"Requires 2 or 4 arguments: \n"
				+"\t"+  "http-server: <port> <session-provider>\n"
				+"\t"+  "session-provider: -sp <port> <username> <password>");
			return;
		}
		var sessionProvider = args[1];
		if(sessionProvider.matches("(\\d{1,3}\\.){3}\\d{1,3}|(\\w+\\.)+\\w{2,3}:\\d+")) {
			spAddr = sessionProvider.split(":")[0];
			spPort = Integer.parseInt(sessionProvider.split(":")[1]);
		} else if(sessionProvider.matches("\\d+")) {
			spAddr = "127.0.0.1";
			spPort = Integer.parseInt(sessionProvider);
		} else {
			getLogger().log(Level.ERROR, 
					"Session provider doesn't match format: " + sessionProvider);
			return;
		}
		getLogger().log(Level.INFO,
				"registered session provider: \n\taddr: " + spAddr + "\n\tport: " + spPort);
		
		Server server = new Server(Integer.parseInt(args[0]), getLogger());
		server.route("/", Main::frontend);
		server.route("\\/[\\w\\d-]+(\\.[\\w\\d-]+)?", Main::frontend);
		
		server.start();
	}
	
	private static String[] getSession() throws IOException {
		var client = new Socket(spAddr, spPort);
		var w = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
		var r = new BufferedReader(new InputStreamReader(client.getInputStream()));
		
		w.write("getSession\n");
		w.flush();
		var session = r.readLine();
		var csrf = r.readLine();
		client.close();
		
		return new String[] {session, csrf};
	}
	
	private static Map<String, Supplier<String>> vars = Map.of(
		"streamURL", () -> fetchStreamURL().orElse("")
	);
	public static void frontend(String method, String resource, Session session) throws IOException {
		if(!method.equals("GET")) {
			getLogger().log(Level.WARNING, "405 \"" + method + "\" Not Allowed");
			session.sendStatus(HttpStatus.METHOD_NOT_ALLOWED);
			return;
		}
		resource = FileUtil.sanitize(resource);
		if(resource.equals("/"))
			resource = "/index.html";
		
		getLogger().log(Level.INFO, "loading template 'frontend" + resource + "'...");
		session.sendStatus(HttpStatus.OK);
		session.sendBody(FileUtil.loadTemplate("frontend" + resource, vars));
	}
	
	public static Optional<String> fetchStreamURL() {
		getLogger().log(Level.INFO, "--- begin fetchStreamURL ---");
		try {
			var doc = Jsoup.connect("https://iptv.nak.org/public/login").get();

			getLogger().log(Level.INFO, "> searching csrf-token");
			var csrf = doc
				.selectFirst("form")
				.getElementsByAttributeValue("type", "hidden")
				.get(0)
				.attr("value");
			getLogger().log(Level.INFO, "* found csrf(" + csrf.length() + "): " + csrf);
			
			var client = HttpClients.createDefault();
			var get = new HttpGet("https://iptv.nak.org/dashboard");
			
			var s = getSession();
			getLogger().log(Level.INFO,
					"using login: \n\tsid: " + s[0] + "\n\tcsrf: " + csrf);
			NameValuePair[] postData = {
				new BasicNameValuePair("iptv_session", s[0]),
				new BasicNameValuePair("iptv_portal", s[1]),
//				new BasicNameValuePair("csrf_token", csrf)
			};
			get.setEntity(new UrlEncodedFormEntity(List.of(postData)));
			
			getLogger().log(Level.INFO, "> executing get request");

			@SuppressWarnings("deprecation")
			var response = client.execute(get);
			
			if(response.getEntity() != null) {
				String res = "";
				String streamURL;
				getLogger().log(Level.INFO, "> reading html-response");
				try(var in = 
						new BufferedReader( 
							new InputStreamReader( response.getEntity().getContent() ))) {
					while(in.ready())
						res += (char) in.read();
				}
				var dashboard = Jsoup.parse(res);
				var btn = dashboard.selectFirst("a.selectbutton");
				if(btn == null) {
					getLogger().log(Level.WARNING, "selectbutton is null! (this is probably because the login failed); dumping html: \n" + res);
					return Optional.empty();
				}
				var eventURL = btn.attr("href");
				getLogger().log(Level.INFO, "* found event url: " + eventURL);
				// format: /events/eventID_0/view/eventID_1
				if(eventURL.contains("/events/")) {
					var eventIDs = eventURL.substring("/events/".length()).split("\\/view\\/");
					getLogger().log(Level.INFO, "* found event ids(" + eventIDs.length + "):");
					for(var eventID : eventIDs)
						getLogger().log(Level.INFO, eventID);
	
					if(eventIDs.length == 2) {
						var url = "https://stream1.nac-cdn.org/poster/" + eventIDs[0] + "/" + eventIDs[1] + "/high/index.m3u8";
						getLogger().log(Level.INFO, "* found url(" + url.length() + "): " + url + "\n --- complete ---");
						return Optional.of(url);
					}
				}
				
				getLogger().log(Level.INFO, "> searching stream url in html-response(" + res.length() + ")");
				var streamURLPattern = Pattern.compile("https:\\/\\/stream\\d\\.nac-cdn\\.org\\/poster\\/([a-fA-F0-9-]+\\/){2}high\\/index\\.m3u8");
				
				var matcher = streamURLPattern.matcher(res);
				if(!matcher.find()) {
					getLogger().log(Level.WARNING, "* No stream url found! Dumping html");
					getLogger().log(Level.WARNING, res);
					return Optional.empty();
				}
				
				streamURL = res.substring(matcher.start(), matcher.end());
				getLogger().log(Level.INFO, "--- complete ---");
				getLogger().log(Level.INFO, "* found url(" + streamURL.length() + "): " + streamURL);
				return Optional.of(streamURL);
			} else
				getLogger().log(Level.WARNING, "* could not create response-entity");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}
}
