package de.appsist.service.pki.model;

import org.vertx.java.core.json.JsonObject;

/**
 * Model for process instances.
 * @author simon.schwantzer(at)im-c.de
 */
public class ProcessInstance {
	private final JsonObject json;
	
	public ProcessInstance(JsonObject json) throws IllegalArgumentException {
		validateJson(json);
		this.json = json;
	}
	
	private static void validateJson(JsonObject json) throws IllegalArgumentException {
		String id = json.getString("id");
		if (id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid or missing [id].");
		}
		
		String processId = json.getString("id");
		if (processId == null || processId.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing or invalid process identifier [processId].");
		}
		
		Boolean isRunning = json.getBoolean("isRunning");
		if (isRunning == null) {
			throw new IllegalArgumentException("Missing running information [isRunning].");
		}
	}
	
	public JsonObject asJson() {
		return json;
	}
	
	public String getId() {
		return json.getString("id");
	}
	
	public String getProcessId() {
		return json.getString("processId");
	}
	
	public boolean isRunning() {
		return json.getBoolean("isRunning");
	}
	
	public String getUserId() {
		return json.getString("userId");
	}
	
	public String getParent() {
		return json.getString("parent");
	}
	
	public JsonObject getContext() {
		return json.getObject("context");
	}
}
