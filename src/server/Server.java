package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Server extends Thread {
	private List<Session> sessions;
	private Map<String, RouteEndpoint> routes;
	private ServerSocket socket;
	private Logger logger;
	
	public Server(int port, Logger logger) throws IOException {
		socket = new ServerSocket(port);
		this.logger = logger;
		routes = new HashMap<>();
		sessions = new ArrayList<>();
	}
	
	public void route(String route, RouteEndpoint endpoint) {
		routes.put(route, endpoint);
	}
	
	public void run() {
		logger.log(Level.INFO, 
				"Serving HTTP on " + socket.getLocalSocketAddress());
		while(!socket.isClosed()) {
			try {
				var client = socket.accept();
				logger.log(Level.INFO,
						"connected: " + client.getInetAddress().getHostAddress());
				
				new Thread(() -> {
					try {
						handle(client);
						client.close();
						logger.log(Level.INFO,
								"disconnected: " + client.getInetAddress().getHostAddress());
					} catch (IOException e) {
						logger.log(Level.WARNING, 
								"I/O Exception: " + e.getMessage());
					}
				}).start();
			} catch (IOException e) {
				logger.log(Level.WARNING, 
						"I/O Exception: " + e.getMessage());
			}
		}
	}
	
	private void handle(Socket client) throws IOException {
		var reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
		
		String[] request = null;
		var headers = new HashMap<>();
		var line = "";
		while((line = reader.readLine()) != null && !line.isBlank()) {
			if(request == null) {
				request = line.split(" ");
				if(request.length != 3) {
					logger.log(Level.WARNING, 
							"[" + client.getInetAddress().getHostAddress() + "] < Bad request: " + line);
					var session = new Session(client, request[2]);
					session.sendStatus(HttpStatus.BAD_REQUEST);
					session.complete();
					return;
				}
				logger.log(Level.INFO, 
						"[" + client.getInetAddress().getHostAddress() + "] " + line);
				continue;
			}
			
			if(!line.matches("([A-Za-z-]+):\\s(.+)")) {
				logger.log(Level.WARNING,
						"Illegal Header Formatting: " + line);
				return;
			}
			var header = line.split(":");
			headers.put(header[0].trim(), header[1].trim());
		}
		if(request == null)
			return;
		
		var httpVersion = request[0];
		var resource = request[1];
		var session = new Session(client, request[2]);
		if(!resource.startsWith("/")) {
			session.sendStatus(HttpStatus.BAD_REQUEST);
			session.complete();
			logger.log(Level.WARNING, 
					"[" + client.getInetAddress().getHostAddress() + "] < Bad request: " + resource);
			return;
		}
		
		sessions.add(session);
		
		getRoute(resource)
			.orElseGet(() -> (m,r,s) -> s.sendStatus(HttpStatus.NOT_FOUND))
			.handle(
				httpVersion,
				resource,
				session
			);
		
		if(!session.isDisposed())
			session.complete();
		
		sessions.remove(session);
	}
	
	private Optional<RouteEndpoint> getRoute(String route) {
		if(routes.containsKey(route))
			return Optional.of(routes.get(route));
		for(var entry : routes.entrySet())
			if(route.matches(entry.getKey()))
				return Optional.of(entry.getValue());
		return Optional.empty();
	}
}
