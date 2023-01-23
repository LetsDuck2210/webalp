package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;

public class SessionProvider extends Thread {
	private ServerSocket server;
	private String credential, password;
	private Logger logger;
	
	public SessionProvider(int port, String credential, String password, Logger logger) {	
		try {
			server = new ServerSocket(port);
			this.credential = credential;
			this.password = password;
			this.logger = logger;
		} catch (IOException e) {
			logger.log(Level.ERROR, 
					"I/O Exception: " + e.getMessage());
		}
	}
	
	public void run() {
		while(!server.isClosed()) {
			try {
				var client = server.accept();
				
				new Thread(() -> {
					try {
						client.setSoTimeout(3000);
						
						var r = new BufferedReader(new InputStreamReader(client.getInputStream()));
						
						var in = r.readLine();
						if(!in.equals("getSession")) {
							client.close();
							return;
						}
						var w = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
						
						var session = getSession();
						if(session.length != 2) {
							logger.log(Level.WARNING,
									"couldn't find session");
							client.close();
							return;
						}
						logger.log(Level.INFO,
								"found session: \n\tid: " + session[0] + "\n\tportal: " + session[1]);
						for(var v : session) {
							w.write(v);
							w.newLine();
						}
						w.flush();
						
						client.close();
					} catch (SocketException e) {
						logger.log(Level.ERROR,
								"Error setting Socket properties: " + e.getMessage());
					} catch (IOException e) {
						logger.log(Level.WARNING, 
								"I/O Exception: " + e.getMessage());
					} catch (ProtocolException e) {
						logger.log(Level.ERROR, 
								"ProtocolException: " + e.getMessage());
					}
				}).start();
			} catch(IOException e) {
				logger.log(Level.WARNING, 
						"I/O Exception: " + e.getMessage());
			}
		}
	}
	
	private String[] getSession() throws IOException, ProtocolException {
		var doc = Jsoup.connect("https://iptv.nak.org/public/login").get();

		logger.log(Level.INFO,
				"searching csrf-token");
		var csrf = doc
			.selectFirst("form")
			.getElementsByAttributeValue("type", "hidden")
			.get(0)
			.attr("value");
		logger.log(Level.INFO,
				"* found csrf(" + csrf.length() + "): " + csrf);
		
		var client = HttpClients.createDefault();
		var post = new HttpPost("https://iptv.nak.org/public/login");
		NameValuePair[] data = {
			new BasicNameValuePair("credential", credential),
			new BasicNameValuePair("password", password),
			new BasicNameValuePair("csrf_token", csrf)
		};
		post.setEntity(new UrlEncodedFormEntity(List.of(data)));
		
		logger.log(Level.INFO,
				"executing post request");
		
		@SuppressWarnings("deprecation")
		var response = client.execute(post);
		
		var session = "";
		var portal = "";
		for(var setcookie : response.getHeaders("Set-Cookie")) {
			logger.log(Level.INFO,
					"Set-Cookie: " + setcookie.getValue());
			var val = setcookie.getValue();
			if(val.startsWith("iptv_session=")) {
				var sem = val.indexOf(';');
				session = val.substring("iptv_session=".length(), sem > 0 ? sem : val.length());
				continue;
			}
			if(val.startsWith("iptv_portal=")) {
				var sem = val.indexOf(';');
				portal = val.substring("iptv_portal=".length(), sem > 0 ? sem : val.length());
			}
		}
		
		return new String[] {
			session,
			portal
		};
	}
}
