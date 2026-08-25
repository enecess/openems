package io.openems.backend.metadata.dummy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.openems.backend.authentication.api.AuthUserPasswordAuthenticationService;
import io.openems.backend.authentication.api.model.PasswordAuthenticationResult;
import io.openems.common.exceptions.OpenemsError;

@Component(//
		service = AuthUserPasswordAuthenticationService.class, //
		immediate = true //
)
public class DummyUserAuthenticationService implements AuthUserPasswordAuthenticationService {

	private static final String PASSWORD_REQUIRED = "Password must not be blank.";
	private static final String USERNAME_REQUIRED = "Username must not be blank.";

	private final Map<String, PasswordAuthenticationResult> sessions = new ConcurrentHashMap<>();

	@Reference
	private MetadataDummy metadata;

	@Override
	public CompletableFuture<PasswordAuthenticationResult> authenticateWithPassword(String username,
			String password) {
		if (username == null || username.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalArgumentException(USERNAME_REQUIRED));
		}
		if (password == null || password.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalArgumentException(PASSWORD_REQUIRED));
		}
		var normalizedUsername = username.strip();
		var token = UUID.randomUUID().toString();
		var result = new PasswordAuthenticationResult(normalizedUsername, normalizedUsername, token);
		this.sessions.put(token, result);
		return CompletableFuture.completedFuture(result);
	}

	@Override
	public CompletableFuture<PasswordAuthenticationResult> authenticateWithToken(String token) {
		if (token == null || token.isBlank()) {
			return CompletableFuture.failedFuture(OpenemsError.COMMON_AUTHENTICATION_FAILED.exception());
		}
		var result = this.sessions.get(token);
		if (result == null) {
			return CompletableFuture.failedFuture(OpenemsError.COMMON_AUTHENTICATION_FAILED.exception());
		}
		return CompletableFuture.completedFuture(result);
	}

	@Override
	public CompletableFuture<Void> logout(String token) {
		if (token != null) {
			this.sessions.remove(token);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Deactivate
	void deactivate() {
		this.sessions.clear();
	}

}
