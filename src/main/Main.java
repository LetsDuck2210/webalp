package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpPost;
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
	
	private static String username, password;

	public static void main(String[] args) throws IOException {
		if(args.length != 3) {
			getLogger().log(Level.WARNING, "Requires 3 arguments: <port> <username> <password>");
			return;
		}
		((DefaultLogger) getLogger()).setLevel(Level.INFO);
		
		username = args[1];
		password = args[2];
		
		Server server = new Server(Integer.parseInt(args[0]), getLogger());
		server.route("/", Main::frontend);
		server.route("\\/[\\w\\d-]+(\\.[\\w\\d-]+)?", Main::frontend);
		
		server.start();
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
		var content = FileUtil.loadTemplate("frontend" + resource, vars);
		if(content.isPresent()) {
			session.sendStatus(HttpStatus.OK);
			session.sendBody(content.get());
		} else {
			session.sendStatus(HttpStatus.NOT_FOUND);
			var errFile = FileUtil.loadTemplate("frontend/error/generic.html", Map.of(
				"errormsg", () -> "404 File Not Found",
				"errortitle", () -> "404 Not Found",
				"redirect", () -> "true"
			));
			if(errFile.isPresent())
				session.sendBody(errFile.get());
			getLogger().log(Level.WARNING, "404 \"" + resource + "\" Not Found");
		}
	}
	
	private static String lastURL;	// the url when it was last updated
	private static long lastUpdate;	// millis since 00:00 Jan 1, 1970
	private static final long UPDATE_DELAY = 40_000_000; // update about twice a day
	public static Optional<String> fetchStreamURL() {
		getLogger().log(Level.DEBUG, 
				"url requested at: " + System.currentTimeMillis() 
						+ ",\n\t last: " + lastUpdate 
						+ ",\n\t dif: " + (System.currentTimeMillis() - lastUpdate)
						+ ",\n\t last url: " + lastURL);
		if(System.currentTimeMillis() - lastUpdate < UPDATE_DELAY
				&& lastURL != null && !lastURL.isBlank()) {
			getLogger().log(Level.INFO, "using last url: " + lastURL);
			return Optional.of(lastURL);
		}
		
		lastUpdate = System.currentTimeMillis();
		
		getLogger().log(Level.DEBUG, "--- begin fetchStreamURL ---");
		try {
			var doc = Jsoup.connect("https://iptv.nak.org/public/login").get();
			
			getLogger().log(Level.DEBUG, "> searching csrf-token");
			var csrf = doc
				.selectFirst("form")
				.getElementsByAttributeValue("type", "hidden")
				.get(0)
				.attr("value");
			getLogger().log(Level.DEBUG, "* found csrf(" + csrf.length() + "): " + csrf);
			
			var client = HttpClients.createDefault();
			var post = new HttpPost("https://iptv.nak.org/public/login");
			NameValuePair[] postData = {
				new BasicNameValuePair("credential", username),
				new BasicNameValuePair("password", password),
				new BasicNameValuePair("csrf_token", csrf)
			};
			post.setEntity(new UrlEncodedFormEntity(List.of(postData)));
			
			getLogger().log(Level.DEBUG, "> executing post request");
			
			@SuppressWarnings("deprecation")
			var response = client.execute(post);
			
			if(response.getEntity() != null) {
				String res = "", url = "";
				getLogger().log(Level.DEBUG, "> reading html-response");
				try(var in = 
						new BufferedReader( 
							new InputStreamReader( response.getEntity().getContent() ))) {
					while(in.ready())
						res += (char) in.read();
				}
				
				getLogger().log(Level.DEBUG, "> searching url in html-response(" + res.length() + ")");
				var cdnURL = getCdnURL(res);
				if(cdnURL.isEmpty()) {
					var eventURL = getEventURL(res);
					
					if(eventURL.isEmpty()) {
						getLogger().log(Level.WARNING, "* No url found! Dumping html");
						getLogger().log(Level.WARNING, res);
					}
					
					return eventURL;
				}
				lastURL = cdnURL.get();
				
				getLogger().log(Level.DEBUG, "* found url(" + lastURL.length() + "): " + lastURL);
				getLogger().log(Level.DEBUG, "--- complete ---");
				return Optional.of(url);
			} else
				getLogger().log(Level.WARNING, "* could not create response-entity");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}
	
	private static Optional<String> getCdnURL(String html) {
		var streamURLPattern = Pattern.compile("\\/encoder-[0-9a-fA-F-\\/]+\\.m3u8");
		var cdnURLPattern = Pattern.compile("https:\\/\\/cdn-[0-9a-fA-F-\\\\/]+\\.cloud\\.nac-cdn\\.org");
		
		var streamURLmatcher = streamURLPattern.matcher(html);
		var cdnURLmatcher = cdnURLPattern.matcher(html);
		if(!streamURLmatcher.find() || !cdnURLmatcher.find())
			return Optional.empty();
		
		var streamURL = html.substring(streamURLmatcher.start(), streamURLmatcher.end());
		var cdnURL = html.substring(cdnURLmatcher.start(), cdnURLmatcher.end());
		var url = cdnURL + streamURL;
		return Optional.of(url);
	}
	private static Optional<String> getEventURL(String html) {
		var dashboard = Jsoup.parse(html);
		var btn = dashboard.selectFirst("a.selectbutton");
		if(btn == null) {
			getLogger().log(Level.WARNING, "selectbutton is null! (this is probably because the login failed); dumping html: \n" + html);
			return Optional.empty();
		}
		
		var eventURL = btn.attr("href");
		getLogger().log(Level.DEBUG, "* found event url: " + eventURL);
		// format: /events/eventID_0/view/eventID_1
		if(eventURL.contains("/events/")) {
			var eventIDs = eventURL.substring("/events/".length()).split("\\/view\\/");
			getLogger().log(Level.DEBUG, "* found event ids(" + eventIDs.length + "):");
			for(var eventID : eventIDs)
				getLogger().log(Level.DEBUG, eventID);
			
			if(eventIDs.length == 2) {
				var url = "https://stream1.nac-cdn.org/poster/" + eventIDs[0] + "/" + eventIDs[1] + "/high/index.m3u8";
				getLogger().log(Level.DEBUG, "* found url(" + url.length() + "): " + url + "\n --- complete ---");
				lastURL = null;
				return Optional.of(url);
			}
		}
		
		return Optional.empty();
	}
}
