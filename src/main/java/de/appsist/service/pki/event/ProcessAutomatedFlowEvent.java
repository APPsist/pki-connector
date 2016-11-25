package de.appsist.service.pki.event;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import de.appsist.commons.event.ProcessEvent;


public class ProcessAutomatedFlowEvent extends ProcessEvent {
	public interface Condition {
		public String getElementForContext(Map<String, Object> context);
	}
	
	private static class IfCondition implements Condition {
		private String jsonPath;
		private String thenElement;
		private String elseElement;
		
		public IfCondition(Map<String, Object> parameters) throws IllegalArgumentException {
			Object entry = parameters.get("jsonpath"); 
			if (entry == null || !(entry instanceof String)) {
				throw new IllegalArgumentException("Missing json path containig the condition to check [jsonpath].");
			}
			jsonPath = (String) entry;

			entry = parameters.get("then");
			if (entry == null || !(entry instanceof String)) {
				throw new IllegalArgumentException("Missing target element when condition is fulfilled [then].");
			}
			thenElement = (String) entry;

			entry = parameters.get("else");
			if (entry == null || !(entry instanceof String)) {
				throw new IllegalArgumentException("Missing target element when condition is not fulfilled [else].");
			}
			elseElement = (String) entry;
		}
		
		@Override
		public String getElementForContext(Map<String, Object> context) {
			boolean condition;
			try {
				condition = JsonPath.read(context, jsonPath);
			} catch (PathNotFoundException|ClassCastException e) {
				condition = false;
			}
			return (condition) ? thenElement : elseElement;
		}
	}
	
	private static class TrueCondition implements Condition {
		private String thenElement;
		
		public TrueCondition(Map<String, Object> parameters) {
			Object entry = parameters.get("then");
			if (entry == null || !(entry instanceof String)) {
				throw new IllegalArgumentException("Missing target element when condition is fulfilled [then].");
			}
			thenElement = (String) entry;
		}
		
		@Override
		public String getElementForContext(Map<String, Object> context) {
			return thenElement; 
		}
	}
	
	public static final String MODEL_ID = "processEvent:automatedFlow";
	private final Condition condition;
	
	/**
	 * Creates the event based on a content map.
	 * @param content Map with the event content.
	 * @throws IllegalArgumentException One or more required fields are missing.
	 */
	@SuppressWarnings("unchecked")
	public ProcessAutomatedFlowEvent(Map<String, Object> content) throws IllegalArgumentException {
		super(content);
		try {
			Map<String, Object> flowCondition = (Map<String, Object>) getPayload().get("flowCondition");
			if (flowCondition == null) {
				throw new IllegalArgumentException("Missing field: payload.flowCondition");
			}
			Object method = flowCondition.get("method");
			if (method == null || !(method instanceof String)) {
				throw new IllegalArgumentException("Missing or invalid condition type [payload.flowCondition.method].");
			}
			Object parameters = flowCondition.get("parameters");
			if (parameters == null || !(parameters instanceof Map<?, ?>)) {
				throw new IllegalArgumentException("Missing or invalid condition parameters [payload.flowCondition.parameters].");
			}
			switch ((String) method) {
			case "if":
				condition = new IfCondition((Map<String, Object>) parameters);
				break;
			case "true":
				condition = new TrueCondition((Map<String, Object>) parameters);
				break;
			default:
				throw new IllegalArgumentException("Unknown condition type: " + (String) method);
			}
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Wrong field type: payload.flowCondition");
		}
	}
	
	public Condition getCondition() {
		return condition;
	}
	
	/**
	 * Returns the default element if set.
	 * @return ID of the element to default to, may be <code>null</code>.
	 */
	public String getDefault() {
		@SuppressWarnings("unchecked")
		Map<String, Object> flowCondition = (Map<String, Object>) getPayload().get("flowCondition");
		return (String) flowCondition.get("default");
	}
}