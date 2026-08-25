package io.openems.backend.uiwebsocket.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.java_websocket.WebSocket;
import org.junit.Test;

import io.openems.backend.authentication.api.AuthUserPasswordAuthenticationService;
import io.openems.backend.authentication.api.model.PasswordAuthenticationResult;
import io.openems.backend.common.metadata.Metadata;
import io.openems.backend.common.test.DummyUser;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.GenericJsonrpcRequest;
import io.openems.common.jsonrpc.request.GetEdgesRequest;
import io.openems.common.jsonrpc.request.LogoutRequest;
import io.openems.common.jsonrpc.response.GetEdgesResponse;
import io.openems.common.utils.JsonUtils;

public class OnRequestTest {

	private static final String TOKEN = "session-token";

	@Test
	public void authenticatedSessionHandlesRequestsWithoutOAuthService() throws Exception {
		var parent = new UiWebsocketImpl();
		parent.metadata = mock(Metadata.class);
		when(parent.metadata.getPageDevice(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

		var wsData = new WsData(null, 10);
		wsData.setToken(TOKEN);
		wsData.setUser(DummyUser.DUMMY_GUEST);

		var websocket = mock(WebSocket.class);
		when(websocket.getAttachment()).thenReturn(wsData);

		var request = new GenericJsonrpcRequest(GetEdgesRequest.METHOD, JsonUtils.buildJsonObject() //
				.addProperty("page", 0) //
				.addProperty("limit", 20) //
				.build());
		var response = new OnRequest(parent).apply(websocket, request).join();

		assertTrue(response instanceof GetEdgesResponse);
		assertEquals(List.of(), ((GetEdgesResponse) response).edgeMetadata);
	}

	@Test
	public void logoutRevokesAuthenticatedSessionToken() throws Exception {
		var authService = new RecordingAuthenticationService();
		var parent = new UiWebsocketImpl();
		parent.userAuthPasswordService = authService;

		var wsData = new WsData(null, 10);
		wsData.setToken(TOKEN);
		wsData.setUser(DummyUser.DUMMY_GUEST);

		var websocket = mock(WebSocket.class);
		when(websocket.getAttachment()).thenReturn(wsData);

		var response = new OnRequest(parent).apply(websocket, new LogoutRequest());

		assertEquals(TOKEN, authService.logoutToken);
		assertFalse(response.isDone());
		assertEquals(Optional.empty(), wsData.getToken());
		assertNull(wsData.getUser());

		authService.logoutResult.complete(null);
		response.join();
	}

	@Test
	public void logoutFailsCleanlyWhenAuthenticationServiceIsUnavailable() throws Exception {
		var parent = new UiWebsocketImpl();
		var wsData = new WsData(null, 10);
		wsData.setToken(TOKEN);
		wsData.setUser(DummyUser.DUMMY_GUEST);

		var websocket = mock(WebSocket.class);
		when(websocket.getAttachment()).thenReturn(wsData);

		CompletableFuture<?> response;
		try {
			response = new OnRequest(parent).apply(websocket, new LogoutRequest());
		} catch (RuntimeException e) {
			throw new AssertionError("Logout must return a failed future when authentication is unavailable", e);
		}

		var exception = assertThrows(CompletionException.class, response::join);
		var cause = (OpenemsNamedException) exception.getCause();
		assertEquals(OpenemsError.COMMON_SERVICE_NOT_AVAILABLE, cause.getError());
		assertEquals(Optional.empty(), wsData.getToken());
		assertNull(wsData.getUser());
	}

	@Test
	public void logoutPropagatesAuthenticationServiceFailure() throws Exception {
		var authService = new RecordingAuthenticationService();
		var parent = new UiWebsocketImpl();
		parent.userAuthPasswordService = authService;

		var wsData = new WsData(null, 10);
		wsData.setToken(TOKEN);
		wsData.setUser(DummyUser.DUMMY_GUEST);

		var websocket = mock(WebSocket.class);
		when(websocket.getAttachment()).thenReturn(wsData);

		var response = new OnRequest(parent).apply(websocket, new LogoutRequest());
		var expected = OpenemsError.COMMON_AUTHENTICATION_FAILED.exception();
		authService.logoutResult.completeExceptionally(expected);

		var exception = assertThrows(CompletionException.class, response::join);
		assertSame(expected, exception.getCause());
		assertEquals(Optional.empty(), wsData.getToken());
		assertNull(wsData.getUser());
	}

	@Test
	public void logoutWithoutTokenIsIdempotentWithoutAuthenticationService() throws Exception {
		var parent = new UiWebsocketImpl();
		var wsData = new WsData(null, 10);
		wsData.setUser(DummyUser.DUMMY_GUEST);

		var websocket = mock(WebSocket.class);
		when(websocket.getAttachment()).thenReturn(wsData);

		new OnRequest(parent).apply(websocket, new LogoutRequest()).join();

		assertEquals(Optional.empty(), wsData.getToken());
		assertNull(wsData.getUser());
	}

	private static final class RecordingAuthenticationService implements AuthUserPasswordAuthenticationService {

		private final CompletableFuture<Void> logoutResult = new CompletableFuture<>();
		private String logoutToken;

		@Override
		public CompletableFuture<PasswordAuthenticationResult> authenticateWithPassword(String username,
				String password) {
			throw new AssertionError("Unexpected password authentication");
		}

		@Override
		public CompletableFuture<PasswordAuthenticationResult> authenticateWithToken(String token) {
			throw new AssertionError("Unexpected token authentication");
		}

		@Override
		public CompletableFuture<Void> logout(String token) {
			this.logoutToken = token;
			return this.logoutResult;
		}
	}
}
