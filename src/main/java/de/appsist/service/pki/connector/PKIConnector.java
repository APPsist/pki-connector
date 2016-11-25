package de.appsist.service.pki.connector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.commons.event.CallActivityEvent;
import de.appsist.commons.event.ManualTaskEvent;
import de.appsist.commons.event.ProcessCancelledEvent;
import de.appsist.commons.event.ProcessCompleteEvent;
import de.appsist.commons.event.ProcessErrorEvent;
import de.appsist.commons.event.ProcessEvent;
import de.appsist.commons.event.ProcessStartEvent;
import de.appsist.commons.event.ProcessTerminateEvent;
import de.appsist.commons.event.ProcessUserRequestEvent;
import de.appsist.commons.event.ServiceTaskEvent;
import de.appsist.commons.event.TaskEvent;
import de.appsist.commons.event.UserTaskEvent;
import de.appsist.commons.util.EventUtil;
import de.appsist.service.pki.event.ProcessAutomatedFlowEvent;
import de.appsist.service.pki.model.ProcessDefinition;
import de.appsist.service.pki.model.ProcessElement;
import de.appsist.service.pki.model.ProcessElementInstance;
import de.appsist.service.pki.model.ProcessInstance;

/**
 * Connector for the process coordination service.
 * @author simon.schwantzer(at)im-c.de
 *
 */
public class PKIConnector {
	static final Logger logger = LoggerFactory.getLogger(PKIConnector.class);
	
	private final HttpClient pkiClient;
	private final String basePath;
	
	private final Set<Handler<TaskEvent>> taskHandlers;
	private final Set<Handler<ProcessStartEvent>> processStartHandlers;
	private final Set<Handler<ProcessCompleteEvent>> processCompleteHandlers;
	private final Set<Handler<ProcessErrorEvent>> processErrorHandlers;
	private final Set<Handler<ProcessTerminateEvent>> processTerminateHandlers;
	private final Set<Handler<ProcessCancelledEvent>> processCancelledHandlers;
	private final Set<Handler<CallActivityEvent>> callActivityHandlers;
	private final Set<Handler<ProcessUserRequestEvent>> processUserRequestHandlers;
	private final Set<Handler<ProcessAutomatedFlowEvent>> processAutomatedFlowHandlers;
	
	private final Map<String, ProcessDefinition> processDefinitionsCache;
	private final Map<String, ProcessInstance> processInstancesCache;
	private final Map<String, ProcessElementInstance> processElementInstancesCache;
	private final Map<String, ProcessElement> processElementsCache;
	
	/**
	 * Creates the connector.
	 * @param vertx Vertx runtime for communication channels.
	 * @param host Hostname to of the pki service.
	 * @param port Port of the pki service.
	 * @param isSecure <code>true</code> if the communication should be ssl secured, otherwise <code>false</code>.
	 * @param basePath Base path of the pki service address.
	 */
	public PKIConnector(Vertx vertx, String host, int port, boolean isSecure, String basePath) {
		pkiClient = vertx.createHttpClient();
		pkiClient.setHost(host);
		pkiClient.setPort(port);
		pkiClient.setSSL(isSecure);
		this.basePath = basePath;
		
		taskHandlers = new HashSet<>();
		processStartHandlers = new HashSet<>();
		processCompleteHandlers = new HashSet<>();
		processErrorHandlers = new HashSet<>();
		processTerminateHandlers = new HashSet<>();
		processCancelledHandlers = new HashSet<>();
		callActivityHandlers = new HashSet<>();
		processUserRequestHandlers = new HashSet<>();
		processAutomatedFlowHandlers = new HashSet<>();
		
		processDefinitionsCache = new HashMap<>();
		processInstancesCache = new HashMap<>();
		processElementInstancesCache = new HashMap<>();
		processElementsCache = new HashMap<>();

		initializeEventBusHandlers(vertx.eventBus());
	}
	
	/**
	 * Helper to load the process definition, the process instance, and the current element instance into the cache.  
	 * @param event Event containing the identifiers. 
	 * @param completeHandler Handler to call when the cache is updated.
	 */
	private void retrieveData(ProcessEvent event, VoidHandler completeHandler) {
		String processId = event.getProcessId();
		String processInstanceId = event.getProcessInstanceId();
		String elementId = event.getElementId();
		
		final ParallelRequestHandler requests = new ParallelRequestHandler(new HashSet<String>(Arrays.asList("processDefinition", "processInstance", "processElement")), completeHandler);
		if (processDefinitionsCache.containsKey(processId)) {
			requests.completeRequest("processDefinition");
		} else {
			getProcessDefinition(processId, new AsyncResultHandler<ProcessDefinition>() {

				@Override
				public void handle(AsyncResult<ProcessDefinition> result) {
					requests.completeRequest("processDefinition");
				}
			});
		}
		if (processInstancesCache.containsKey(processInstanceId)) {
			requests.completeRequest("processInstance");
		} else {
			getProcessInstance(processInstanceId, new AsyncResultHandler<ProcessInstance>() {

				@Override
				public void handle(AsyncResult<ProcessInstance> request) {
					requests.completeRequest("processInstance");
				}
			});
		}
		if (processElementInstancesCache.containsKey(processInstanceId + ":" + elementId)) {
			requests.completeRequest("processElement");
		} else {
			getCurrentElement(processInstanceId, event.getSessionId(), new AsyncResultHandler<ProcessElementInstance>() {

				@Override
				public void handle(AsyncResult<ProcessElementInstance> event) {
					requests.completeRequest("processElement");
				}
			});
		}
	}
	
	/**
	 * Register for events on the event bus.
	 * @param eventBus Event bus to connect to.
	 */
	private void initializeEventBusHandlers(EventBus eventBus) {
		
		// Tasks
		eventBus.registerHandler("appsist:event:" + ManualTaskEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ManualTaskEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ManualTaskEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}

				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<TaskEvent> handler : taskHandlers) {
							handler.handle(event);
						}
						
					}
				});
			}
		});
		eventBus.registerHandler("appsist:event:" + UserTaskEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final UserTaskEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), UserTaskEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<TaskEvent> handler : taskHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
		eventBus.registerHandler("appsist:event:" + ServiceTaskEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ServiceTaskEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ServiceTaskEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<TaskEvent> handler : taskHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
		
		// Process Start
		eventBus.registerHandler("appsist:event:" + ProcessStartEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessStartEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessStartEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<ProcessStartEvent> handler : processStartHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
		
		// Process Complete
		eventBus.registerHandler("appsist:event:" + ProcessCompleteEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessCompleteEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessCompleteEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				for (Handler<ProcessCompleteEvent> handler : processCompleteHandlers) {
					handler.handle(event);
				}
			}
		});
		
		// Process Error
		eventBus.registerHandler("appsist:event:" + ProcessErrorEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessErrorEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessErrorEvent.class);
					logger.info("[PSD] Received process error event: " + message.body().encode());
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				
				for (Handler<ProcessErrorEvent> handler : processErrorHandlers) {
					handler.handle(event);
				}
			}
		});
		
		// Process Terminated
		eventBus.registerHandler("appsist:event:" + ProcessTerminateEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessTerminateEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessTerminateEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				
				for (Handler<ProcessTerminateEvent> handler : processTerminateHandlers) {
					handler.handle(event);
				}
			}
		});
		
		// Process Cancelled
		eventBus.registerHandler("appsist:event:" + ProcessCancelledEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessCancelledEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessCancelledEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				for (Handler<ProcessCancelledEvent> handler : processCancelledHandlers) {
					handler.handle(event);
				}
			}
		});
		
		// Call Activity
		eventBus.registerHandler("appsist:event:" + CallActivityEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final CallActivityEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), CallActivityEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<CallActivityEvent> handler : callActivityHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
		
		// User Requests
		eventBus.registerHandler("appsist:event:" + ProcessUserRequestEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessUserRequestEvent event;
				try {
					event = EventUtil.parseEvent(message.body().toMap(), ProcessUserRequestEvent.class);
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<ProcessUserRequestEvent> handler : processUserRequestHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
		
		// Automated Flows
		eventBus.registerHandler("appsist:event:" + ProcessAutomatedFlowEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				final ProcessAutomatedFlowEvent event;
				try {
					event = new ProcessAutomatedFlowEvent(message.body().toMap());
				} catch (IllegalArgumentException e) {
					logger.warn("Failed to parse event.", e);
					return;
				}
				retrieveData(event, new VoidHandler() {
					@Override
					protected void handle() {
						for (Handler<ProcessAutomatedFlowEvent> handler : processAutomatedFlowHandlers) {
							handler.handle(event);
						}
					}
				});
			}
		});
	}

	/**
	 * Registers a handler for task events. The events are typed, i.e. one of {@link UserTaskEvent}, {@link ManualTaskEvent}, or {@link ServiceTaskEvent}.
	 * @param handler Handler to register.
	 */
	public void registerTaskHandler(Handler<TaskEvent> handler) {
		taskHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for process start events.
	 * @param handler Handler to register.
	 */
	public void registerProcessStartHandler(Handler<ProcessStartEvent> handler) {
		processStartHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for process completion events.
	 * @param handler Handler to register. 
	 */
	public void registerProcessCompleteHandler(Handler<ProcessCompleteEvent> handler) {
		processCompleteHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for process error events.
	 * @param handler Handler to register.
	 */
	public void registerProcessErrorHandler(Handler<ProcessErrorEvent> handler) {
		processErrorHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for process termination events.
	 * @param handler Handler to register.
	 */
	public void registerProcessTerminateHandler(Handler<ProcessTerminateEvent> handler) {
		processTerminateHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for process cancellation events. A process cancellation is performed on behalf of a user.
	 * @param handler Handler to register.
	 */
	public void registerProcessCancelledHandler(Handler<ProcessCancelledEvent> handler) {
		processCancelledHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for call activities. The event is generated when a call activity is reached, the related subprocess in not necessarily instantiated.
	 * @param handler Handler to register.
	 */
	public void registerCallActivityHandler(Handler<CallActivityEvent> handler) {
		callActivityHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for user requests.
	 * @param handler Handler to register.
	 */
	public void registerProcessUserRequestHandler(Handler<ProcessUserRequestEvent> handler) {
		processUserRequestHandlers.add(handler);
	}
	
	/**
	 * Registers a handler for automated flow events.
	 * @param handler Handler to register.
	 */
	public void registerProcessAutomatedFlowHandler(Handler<ProcessAutomatedFlowEvent> handler) {
		processAutomatedFlowHandlers.add(handler);
	}
	
	/**
	 * Instantiates a process.
	 * @param processId ID of the process definition to instantiate.
	 * @param userId ID of the user instantiating the process. May be <code>null</code>.
	 * @param context Context to instantiate process with. May be <code>null</code>.
	 * @param resultHandler Handler for the created process instance.
	 */
	public void instantiateProcess(final String processId, String sessionId, String userId, JsonObject context, final AsyncResultHandler<ProcessInstance> resultHandler) {
		StringBuilder pathBuilder = new StringBuilder(50);
		pathBuilder.append(basePath).append("/processes/").append(processId).append("/instantiate");
		pathBuilder.append("?sid=").append(sessionId);
		if (userId != null) pathBuilder.append("&userId=").append(userId);
		
		HttpClientRequest request = pkiClient.post(pathBuilder.toString(), new TypedHttpResponse<ProcessInstance>(new AsyncResultHandler<ProcessInstance>() {

			@Override
			public void handle(AsyncResult<ProcessInstance> event) {
				if (event.succeeded()) {
					ProcessInstance result = event.result();
					processInstancesCache.put(result.getId(), result);
				}
				resultHandler.handle(event);
			}
		}, ProcessInstance.class));
		if (context != null) {
			JsonObject body = new JsonObject();
			body.putObject("context", context);
			request.end(body.encode());
		} else {
			request.end();
		}
	}
	
	public void next(final String processInstanceId, String sessionId, final String elementId, final AsyncResultHandler<ProcessElementInstance> resultHandler) {
		StringBuilder pathBuilder = new StringBuilder(50);
		pathBuilder.append(basePath).append("/instances/").append(processInstanceId).append("/next").append("?sid=").append(sessionId);
		if (elementId != null) pathBuilder.append("&elementId=").append(elementId);
		pkiClient.post(pathBuilder.toString(), new TypedHttpResponse<ProcessElementInstance>(new AsyncResultHandler<ProcessElementInstance>() {

			@Override
			public void handle(AsyncResult<ProcessElementInstance> event) {
				if (event.succeeded()) {
					ProcessElementInstance result = event.result();
					processElementInstancesCache.put(processInstanceId + ":" + result.getId(), result);
				}
				resultHandler.handle(event);
			}
		}, ProcessElementInstance.class)).end();
	}
	
	public void confirm(final String processInstanceId, String sessionId, final AsyncResultHandler<ProcessInstance> resultHandler) {
		StringBuilder pathBuilder = new StringBuilder(50);
		pathBuilder.append(basePath).append("/instances/").append(processInstanceId).append("/confirm").append("?sid=").append(sessionId);
		pkiClient.post(pathBuilder.toString(), new TypedHttpResponse<ProcessInstance>(new AsyncResultHandler<ProcessInstance>() {

			@Override
			public void handle(AsyncResult<ProcessInstance> event) {
				if (event.succeeded()) {
					ProcessInstance result = event.result();
					processInstancesCache.put(result.getId(), result);
				}
				resultHandler.handle(event);
			}
		}, ProcessInstance.class)).end();
	}
	
	/**
	 * 
	 * @param processInstanceId Identifier of the process instance to cancel.
	 * @param sessionId Session identifier.
	 * @param resultHandler Handler to check if the operation succeeded. May be null.
	 */
	public void cancel(String processInstanceId, String sessionId, final AsyncResultHandler<Void> resultHandler) {
		StringBuilder pathBuilder = new StringBuilder(50);
		pathBuilder.append(basePath).append("/instances/").append(processInstanceId).append("/cancel").append("?sid=").append(sessionId);
		pkiClient.post(pathBuilder.toString(), new Handler<HttpClientResponse>() {
			
			@Override
			public void handle(final HttpClientResponse response) {
				if (resultHandler != null) response.bodyHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						final String body = buffer.toString();
						if (resultHandler != null) resultHandler.handle(new AsyncResult<Void>() {
							
							@Override
							public boolean succeeded() {
								return response.statusCode() == 200;
							}
							
							@Override
							public Void result() {
								return null;
							}
							
							@Override
							public boolean failed() {
								return !succeeded();
							}
							
							@Override
							public Throwable cause() {
								return failed() ? new HttpException(body, response.statusCode()) : null;
							}
						});
					}
				});
			}
		}).end();
	}
	
	public ProcessDefinition getCachedProcessDefinition(String processId) {
		ProcessDefinition processDefintion = processDefinitionsCache.get(processId);
		if (processDefintion == null) {
			logger.warn("Tried to access missing cache entry (process definition): " + processId);
		}
		return processDefintion;
	}
	
	public ProcessInstance getCachedProcessInstance(String processInstanceId) {
		ProcessInstance processInstance = processInstancesCache.get(processInstanceId);
		if (processInstance == null) {
			logger.warn("Tried to access missing cache entry (process instance): " + processInstanceId);
		}
		return processInstance;
	}
	
	public ProcessElement getCachedProcessElement(String processId, String elementId) {
		String key = processId + ":" + elementId;
		ProcessElement processElement = processElementsCache.get(key);
		if (processElement == null) {
			logger.warn("Tried to access missing cache entry (process element): " + key);
		}
		return processElement;
	}
	
	public ProcessElementInstance getCachedProcessElementInstance(String processInstanceId, String elementId) {
		String key = processInstanceId + ":" + elementId;
		ProcessElementInstance instance = processElementInstancesCache.get(key);
		if (instance == null) {
			logger.warn("Tried to access missing cache entry (process element instance): " + key);
		}
		return instance;
	}
	
	public void getProcessDefinition(final String processId, final AsyncResultHandler<ProcessDefinition> resultHandler) {
		pkiClient.get(basePath + "/processes/" + processId, new TypedHttpResponse<ProcessDefinition>(new AsyncResultHandler<ProcessDefinition>() {

			@Override
			public void handle(AsyncResult<ProcessDefinition> event) {
				if (event.succeeded()) {
					processDefinitionsCache.put(processId, event.result());
				}
				resultHandler.handle(event);
			}
		}, ProcessDefinition.class)).end();
	}
	
	public void getProcessInstance(final String processInstanceId, final AsyncResultHandler<ProcessInstance> resultHandler) {
		pkiClient.get(basePath + "/instances/" + processInstanceId, new TypedHttpResponse<ProcessInstance>(new AsyncResultHandler<ProcessInstance>() {

			@Override
			public void handle(AsyncResult<ProcessInstance> event) {
				if (event.succeeded()) {
					processInstancesCache.put(processInstanceId, event.result());
				}
				resultHandler.handle(event);
			}
		}, ProcessInstance.class)).end();
	}
	
	public void getProcessElement(final String processId, final String elementId, final AsyncResultHandler<ProcessElement> resultHandler) {
		pkiClient.get(basePath + "/processes/" + processId + "/elements/" + elementId, new TypedHttpResponse<ProcessElement>(new AsyncResultHandler<ProcessElement>() {

			@Override
			public void handle(AsyncResult<ProcessElement> event) {
				if (event.succeeded()) {
					processElementsCache.put(processId + ":" + elementId, event.result());
				}
				resultHandler.handle(event);
			}
		}, ProcessElement.class)).end();
	}
	
	public void getCurrentElement(final String processInstanceId, String sessionId, final AsyncResultHandler<ProcessElementInstance> resultHandler) {
		StringBuilder pathBuilder = new StringBuilder(50);
		pathBuilder.append(basePath).append("/instances/").append(processInstanceId).append("/currentElement").append("?sid=").append(sessionId);
		pkiClient.get(pathBuilder.toString(), new TypedHttpResponse<ProcessElementInstance>(new AsyncResultHandler<ProcessElementInstance>() {

			@Override
			public void handle(AsyncResult<ProcessElementInstance> event) {
				if (event.succeeded()) {
					ProcessElementInstance elementInstance = event.result();
					processElementInstancesCache.put(processInstanceId + ":" + elementInstance.getId(), elementInstance);
				}
				resultHandler.handle(event);
			}
		}, ProcessElementInstance.class)).end();
	}
	
	/**
	 * Returns the cached process tree with the given process instance as leaf.
	 * @param processInstanceId Identifier for a process instance.
	 * @return Ordered map (parent processes first) with process instances as keys and related process definitions as values.
	 */
	public Map<ProcessInstance, ProcessDefinition> getProcessTree(String processInstanceId) {
		Map<ProcessInstance, ProcessDefinition> map = new LinkedHashMap<>();
		List<ProcessInstance> processInstanceTree = getProcessInstanceTree(processInstanceId);
		for (ProcessInstance processInstance : processInstanceTree) {
			map.put(processInstance, processDefinitionsCache.get(processInstance.getProcessId()));
		}
		return map;
	}
	
	private List<ProcessInstance> getProcessInstanceTree(String processInstanceId) {
		ProcessInstance processInstance = processInstancesCache.get(processInstanceId);
		if (processInstance == null) {
			return Collections.emptyList();
		}
		
		String parentInstanceId = processInstance.getParent();
		List<ProcessInstance> instances;
		if (parentInstanceId != null) {
			instances = getProcessInstanceTree(parentInstanceId);
		} else {
			instances = new ArrayList<>();
		}
		instances.add(processInstance);
		return instances;
	}
}
