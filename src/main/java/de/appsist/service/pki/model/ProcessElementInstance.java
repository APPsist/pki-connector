package de.appsist.service.pki.model;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class ProcessElementInstance extends ProcessElement {
	public ProcessElementInstance(JsonObject json) {
		super(json);
	}
	
	public String getPreviousElement() {
		return json.getString("previousElement");
	}
	
	public List<String> getNextElements() {
		List<String> nextElements = new ArrayList<>();
		JsonArray nextElementsArray = json.getArray("nextElements");
		for (Object entry : nextElementsArray) {
			nextElements.add((String) entry);
		}
		return nextElements;
	}
	
	public JsonObject getExectionInfo() {
		return json.getObject("executionInfo");
	}
}
