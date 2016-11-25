package de.appsist.service.pki.model;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class ProcessElement {
	protected final JsonObject json;
	private final ProcessElementType type;
	
	public ProcessElement(JsonObject json) throws IllegalArgumentException {
		validateJson(json);
		this.json = json;
		type = ProcessElementType.getValueFor(json.getString("type"));
	}
	
	private static void validateJson(JsonObject json) throws IllegalArgumentException {
		String id = json.getString("id");
		if (id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing or invalid identifier [id].");
		}
		
		String label = json.getString("label");
		if (label == null || label.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing element [label].");
		}
		
		JsonArray nextElementsArray = json.getArray("nextElements");
		if (nextElementsArray != null) {
			for (Object entry : nextElementsArray) {
				if (!(entry instanceof String)) {
					throw new IllegalArgumentException("Elements of [nextElements] must be strings.");
				}
			}
		}
	}
	
	public JsonObject asJson() {
		return json;
	}
	
	public String getId() {
		return json.getString("id");
	}
	
	public String getLabel() {
		return json.getString("label");
	}
	
	public ProcessElementType getType() {
		return type;
	}
	
	public JsonArray getTriggers() {
		return json.getArray("triggers");
	}
	
	public JsonArray getEvents() {
		return json.getArray("events");
	}
	
	public JsonArray getServiceCalls() {
		return json.getArray("serviceCalls");
	}
}
