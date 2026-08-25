package io.openems.backend.metadata.dummy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletionException;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

public class DummyUserAuthenticationServiceTest {

	@Test
	public void testAuthenticateWithPasswordCreatesSession() {
		var sut = new DummyUserAuthenticationService();

		var result = sut.authenticateWithPassword(" test-user ", "password").join();

		assertEquals("test-user", result.userId());
		assertEquals("test-user", result.login());
		assertNotNull(result.token());
		assertFalse(result.token().isBlank());
	}

	@Test
	public void testAuthenticateWithPasswordCreatesDistinctTokens() {
		var sut = new DummyUserAuthenticationService();

		var first = sut.authenticateWithPassword("test-user", "password").join();
		var second = sut.authenticateWithPassword("test-user", "password").join();

		assertNotEquals(first.token(), second.token());
	}

	@Test
	public void testAuthenticateWithTokenRestoresSession() {
		var sut = new DummyUserAuthenticationService();
		var passwordResult = sut.authenticateWithPassword("test-user", "password").join();

		var tokenResult = sut.authenticateWithToken(passwordResult.token()).join();

		assertEquals(passwordResult, tokenResult);
	}

	@Test
	public void testAuthenticateWithTokenRejectsInvalidToken() {
		var sut = new DummyUserAuthenticationService();

		for (var token : new String[] { null, " ", "unknown-token" }) {
			var exception = assertThrows(CompletionException.class,
					() -> sut.authenticateWithToken(token).join());
			assertTrue(exception.getCause() instanceof OpenemsNamedException);
		}
	}

	@Test
	public void testAuthenticateWithPasswordRejectsBlankCredentials() {
		var sut = new DummyUserAuthenticationService();

		for (var credentials : new String[][] { //
				{ null, "password" }, //
				{ " ", "password" }, //
				{ "test-user", null }, //
				{ "test-user", " " } //
		}) {
			var exception = assertThrows(CompletionException.class,
					() -> sut.authenticateWithPassword(credentials[0], credentials[1]).join());
			assertTrue(exception.getCause() instanceof IllegalArgumentException);
		}
	}

	@Test
	public void testLogoutRevokesSessionAndIsIdempotent() {
		var sut = new DummyUserAuthenticationService();
		var result = sut.authenticateWithPassword("test-user", "password").join();

		sut.logout(result.token()).join();

		var exception = assertThrows(CompletionException.class,
				() -> sut.authenticateWithToken(result.token()).join());
		assertTrue(exception.getCause() instanceof OpenemsNamedException);
		sut.logout(result.token()).join();
	}

	@Test
	public void testDeactivateRevokesAllSessions() {
		var sut = new DummyUserAuthenticationService();
		var result = sut.authenticateWithPassword("test-user", "password").join();

		sut.deactivate();

		var exception = assertThrows(CompletionException.class,
				() -> sut.authenticateWithToken(result.token()).join());
		assertTrue(exception.getCause() instanceof OpenemsNamedException);
	}

}
