package de.appsist.service.pki.model;

public enum ProcessElementType {
	USER_TASK,
	MANUAL_TASK,
	SERVICE_TASK,
	CALL_ACTIVITY,
	START_EVENT,
	END_EVENT,
	EXCLUSIVE_GATEWAY,
	OTHER;
	
	public static ProcessElementType getValueFor(String typeString) throws IllegalArgumentException {
		if (typeString == null) throw new IllegalArgumentException("Missing process element [type].");
		switch (typeString) {
		case "userTask":
			return USER_TASK;
		case "manualTask":
			return MANUAL_TASK;
		case "serviceTask":
			return SERVICE_TASK;
		case "callActivity":
			return CALL_ACTIVITY;
		case "startEvent":
			return START_EVENT;
		case "endEvent":
			return END_EVENT;
		case "exclusiveGateway":
			return EXCLUSIVE_GATEWAY;
		default:
			return OTHER;
		}
	}
}
