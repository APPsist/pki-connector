package de.appsist.service.pki.connector;

import java.util.Set;

import org.vertx.java.core.VoidHandler;

public class ParallelRequestHandler {
	private Set<String> requests;
	private VoidHandler completeHandler;
	
	public ParallelRequestHandler(Set<String> requests, VoidHandler completeHandler) {
		this.requests = requests;
		this.completeHandler = completeHandler;
	}
	
	public void completeRequest(String requestId) throws IllegalArgumentException {
		boolean requestExisted = requests.remove(requestId);
		if (!requestExisted) {
			throw new IllegalArgumentException("Unknown request.");
		}
		if (requests.isEmpty()) {
			completeHandler.handle(null);
		}
	}

}
