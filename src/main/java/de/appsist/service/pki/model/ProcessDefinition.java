package de.appsist.service.pki.model;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Model for a process definition.
 * @author simon.schwantzer(at)im-c.de
 */
public class ProcessDefinition {
	private final JsonObject json;
	
	public enum Type {
		BPMN,
		OTHER
	}
	
	/**
	 * Creates a process definition wrapping the giving JSON object. 
	 * @param json JSON object representing the model.
	 * @throws IllegalArgumentException The given JSON object is not an valid representation of the model.
	 */
	public ProcessDefinition(JsonObject json) throws IllegalArgumentException {
		validateJson(json);
		this.json = json;
	}
	
	private static void validateJson(JsonObject json) throws IllegalArgumentException {
		String id = json.getString("id");
		if (id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid or missing [id]");
		}
		
		String type = json.getString("type");
		try {
			Type.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalArgumentException("Invalid or missing process [type].", e); 
		}
		
		String startElement = json.getString("startElement");
		if (startElement == null || startElement.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid or missing [startElement].");
		}
	}
	
	/**
	 * Returns the JSON object wrapped by this model.
	 * @return JSON object for the model.
	 */
	public JsonObject asJson() {
		return json;
	}
	
	/**
	 * Returns the identifier of the process definition.
	 * @return Process definition identifier.
	 */
	public String getId() {
		return json.getString("id");
	}
	
	/**
	 * Returns the type of the process definition.
	 * @return Specification type of the process definition.
	 */
	public Type getType() {
		return Type.valueOf(json.getString("type"));
	}
	
	/**
	 * Returns the label of the process.
	 * @return Label of the process, may be <code>null</code>.
	 */
	public String getLabel() {
		return json.getString("label");
	}
	
	/**
	 * Returns the description of the process.
	 * @return Description of the process, may be <code>null</code>.
	 */
	public String getDescription() {
		return json.getString("description");
	}
	
	/**
	 * Returns the start element of the process.
	 * @return Identifier of the start element.
	 */
	public String getStartElementId() {
		return json.getString("startElement");
	}
	
	public JsonObject getLocalData() {
		return json.getObject("localData");
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
