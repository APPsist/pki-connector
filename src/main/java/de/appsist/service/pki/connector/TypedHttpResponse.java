package de.appsist.service.pki.connector;

import java.lang.reflect.InvocationTargetException;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;

public class TypedHttpResponse<E> implements Handler<HttpClientResponse> {
	private final AsyncResultHandler<E> resultHandler;
	private final Class<E> clazz;

	public TypedHttpResponse(AsyncResultHandler<E> resultHandler, Class<E> clazz) {
		this.resultHandler = resultHandler;
		this.clazz = clazz;
	}
	
	@Override
	public void handle(final HttpClientResponse response) {
		response.bodyHandler(new Handler<Buffer>() {
			
			@Override
			public void handle(Buffer buffer) {
				final String body = buffer.toString();
				resultHandler.handle(new AsyncResult<E>() {
					
					@Override
					public boolean succeeded() {
						return response.statusCode() == 200;
					}
					
					@Override
					public E result() {
						if (succeeded() && !body.isEmpty()) {
							JsonObject json = new JsonObject(body);
							try {
								E result = clazz.getDeclaredConstructor(JsonObject.class).newInstance(json);
								return result;
							} catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchMethodException e) {
								PKIConnector.logger.warn("Failed to decode process instance.", e);
								return null;
							}
						} else {
							return null;
						}
						
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
}