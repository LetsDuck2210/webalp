package server;

import java.io.IOException;

public interface RouteEndpoint {
	public void handle(String method, String resource, Session session) throws IOException;
}
