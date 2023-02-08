package server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Session {
	private boolean disposed = false;
	
	// request
	private BufferedWriter writer;
	private Map<String, String> requestHeaders;
	private String httpVersion;
	
	// response
	private HttpStatus status;
	
	public Session(Socket client, String httpVersion) throws IOException {
		this(client, new HashMap<>(), httpVersion);
	}
	public Session(Socket client, Map<String,String> requestHeaders, String httpVersion) throws IOException {
		this.requestHeaders = requestHeaders;
		this.httpVersion = httpVersion;
		writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
	}
	public void requestHeader(String key, String value) {
		requestHeaders.put(key, value);
	}
	public Optional<String> requestHeader(String key) {
		return Optional.ofNullable(requestHeaders.get(key));
	}
	public Map<String,String> getRequestHeaders() {
		return Map.copyOf(requestHeaders);
	}
	public String getHttpVersion() {
		return httpVersion;
	}
	
	/**
	 * sends a status code with http version to the client
	 * @param status the http status code
	 * @throws IOException If an I/O error occurs (e.g. the client disconnects)
	 * @throws IllegalStateException If the status has already been set before or the session has been disposed
	 * */
	public void sendStatus(HttpStatus status) throws IOException {
		if(disposed)
			throw new IllegalStateException("Session is disposed");
		if(this.status != null)
			throw new IllegalStateException("Status has already been set");
		
		this.status = status;
		writer.write(httpVersion + " " + status.code + " " + status + "\r\n");
	}
	/**
	 * sends a header to the client
	 * @throws IOException If an I/O error occurs (e.g. the client disconnects)
	 * @throws IllegalStateException If the status hasn't been set or the session has been disposed
	 * */
	public void sendHeader(String key, String value) throws IOException {
		if(disposed)
			throw new IllegalStateException("Session is disposed");
		if(status == null)
			throw new IllegalStateException("Status has not been set");
		writer.write(key + "=" + value + "\r\n");
	}
	/**
	 * sends the body of the response to the client
	 * @throws IOException If an I/O error occurs (e.g. the client disconnects)
	 * @throws IllegalStateException If the status hasn't been set or the session has been disposed
	 * */
	public void sendBody(String body) throws IOException {
		if(disposed)
			throw new IllegalStateException("Session is disposed");
		if(status == null)
			throw new IllegalStateException("Status has not been set");
		writer.write("\r\n" + body + "\r\n");
	}
	
	/**
	 * completes the transaction and disposes this session
	 * 
	 * @throws IOException If an I/O error occurs (e.g. the client disconnects)
	 * @throws IllegalStateException If the session has already been disposed
	 * */
	public void complete() throws IOException {
		if(disposed)
			throw new IllegalStateException("Session is disposed");
		writer.write("\r\n");
		writer.flush();
		writer.close();
		
		writer = null;
		requestHeaders = null;
		status = null;
		disposed = true;
	}
	
	
	public boolean isDisposed() {
		return disposed;
	}
}
