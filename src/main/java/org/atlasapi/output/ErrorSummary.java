package org.atlasapi.output;

import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ImmutableMap;
import com.metabroadcast.common.http.HttpStatusCode;
import com.metabroadcast.common.webapp.query.DateTimeInQueryParser.MalformedDateTimeException;

public class ErrorSummary {
	
	private static class AtlasExceptionBuilder {
		
		private final String friendly;
		private final HttpStatusCode httpStatus;

		public AtlasExceptionBuilder(String friendlyCode, HttpStatusCode httpStatusCode) {
			this.friendly = friendlyCode;
			this.httpStatus = httpStatusCode;
		}

		public String friendly() {
			return friendly;
		}

		public HttpStatusCode httpStatus() {
			return httpStatus;
		}
		
		public ErrorSummary build(Exception exception) {
			return new ErrorSummary(exception).withErrorCode(friendly()).withStatusCode(httpStatus());
		}
	}
	
	private static class ExceptionExposingAtlasExceptionBuilder extends AtlasExceptionBuilder{

		public ExceptionExposingAtlasExceptionBuilder(String friendlyCode, HttpStatusCode httpStatusCode) {
			super(friendlyCode, httpStatusCode);
		}
		
		public ErrorSummary build(Exception exception) {
			return new ErrorSummary(exception).withErrorCode(friendly()).withStatusCode(httpStatus()).withMessage(exception.getMessage());
		}
	}
	
	private static Map<Class<? extends Exception>, AtlasExceptionBuilder> exceptionCodes = exceptionMap();
	
	public static ErrorSummary forException(Exception exception) {
		AtlasExceptionBuilder builder = exceptionCodes.get(exception.getClass());
		if (builder != null) {
			return builder.build(exception);
		} else {
			return new ErrorSummary(exception);
		}
	}
	
	private static Map<Class<? extends Exception>, AtlasExceptionBuilder> exceptionMap() {
		return ImmutableMap.<Class<? extends Exception>, AtlasExceptionBuilder>of(
			IllegalArgumentException.class, new ExceptionExposingAtlasExceptionBuilder("BAD_QUERY_ATTRIBUTE", HttpStatusCode.BAD_REQUEST),
			MalformedDateTimeException.class, new ExceptionExposingAtlasExceptionBuilder("BAD_DATE_TIME_VALUE", HttpStatusCode.BAD_REQUEST)
		);
	}

	private String id;
	private Exception exception;
	private String errorCode = "INTERNAL_ERROR";
	private HttpStatusCode statusCode = HttpStatusCode.SERVER_ERROR;
	private String message = "An internal server error occurred";
	
	public ErrorSummary(Exception exception) {
		this.exception = exception;
	    this.id = UUID.randomUUID().toString();
	}
	
	public ErrorSummary() {}

	public String id() {
		return id;
	}
	
	public Exception exception() {
		return exception;
	}

	public ErrorSummary withStatusCode(HttpStatusCode statusCode) {
		this.statusCode = statusCode;
		return this;
	}

	public HttpStatusCode statusCode() {
		return statusCode;
	}
	
	public ErrorSummary withErrorCode(String errorCode) {
		this.errorCode = errorCode;
		return this;
	}
	public String errorCode() {
		return errorCode;
	}
	
	public ErrorSummary withMessage(String message) {
		this.message = message;
		return this;
	}
	public String message() {
		return this.message;
	}
}
