package io.openems.common.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;
import org.slf4j.Logger;

public class AbstractWebsocketServerTest {

	@Test
	public void shouldHandleNotificationsFromTheSameConnectionConcurrently() throws Exception {
		final var firstStarted = new CountDownLatch(1);
		final var releaseFirst = new CountDownLatch(1);
		final var secondFinished = new CountDownLatch(1);
		final var order = Collections.synchronizedList(new ArrayList<String>());

		try (final var server = new TestWebsocketServer((ws, notification) -> {
			switch (notification.getMethod()) {
			case "first" -> {
				firstStarted.countDown();
				await(releaseFirst);
				order.add("first");
			}
			case "second" -> {
				order.add("second");
				secondFinished.countDown();
			}
			default -> fail("Unexpected notification: " + notification.getMethod());
			}
		})) {
			server.start();
			final var clientError = new AtomicReference<Exception>();
			final var client = new WebSocketClient(new URI("ws://localhost:" + awaitPort(server))) {

				@Override
				public void onOpen(ServerHandshake handshake) {
				}

				@Override
				public void onMessage(String message) {
				}

				@Override
				public void onClose(int code, String reason, boolean remote) {
				}

				@Override
				public void onError(Exception ex) {
					clientError.compareAndSet(null, ex);
				}
			};

			try {
				assertTrue(client.connectBlocking(2, SECONDS));
				client.send("{\"jsonrpc\":\"2.0\",\"method\":\"first\",\"params\":{}}");
				assertTrue(firstStarted.await(2, SECONDS));

				client.send("{\"jsonrpc\":\"2.0\",\"method\":\"second\",\"params\":{}}");
				assertTrue(secondFinished.await(2, SECONDS));

				releaseFirst.countDown();
				for (var attempt = 0; attempt < 100 && order.size() < 2; attempt++) {
					Thread.sleep(20);
				}
				assertEquals(List.of("second", "first"), order);
				assertNull(clientError.get());
			} finally {
				releaseFirst.countDown();
				client.closeBlocking();
			}
		}
	}

	private static int awaitPort(AbstractWebsocketServer<?> server) throws InterruptedException {
		for (var attempt = 0; attempt < 100; attempt++) {
			final var port = server.getPort();
			if (port != 0) {
				return port;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("WebSocket server did not bind to a port");
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class TestWebsocketServer extends AbstractWebsocketServer<WsData>
			implements AutoCloseable {

		private final OnNotification onNotification;

		private TestWebsocketServer(OnNotification onNotification) {
			super("WebsocketServerTest", 0, 2);
			this.onNotification = onNotification;
		}

		@Override
		protected WsData createWsData(WebSocket ws) {
			return new WsData(ws);
		}

		@Override
		protected OnOpen getOnOpen() {
			return OnOpen.NO_OP;
		}

		@Override
		protected OnRequest getOnRequest() {
			return OnRequest.NO_OP;
		}

		@Override
		protected OnNotification getOnNotification() {
			return this.onNotification;
		}

		@Override
		protected OnError getOnError() {
			return OnError.NO_OP;
		}

		@Override
		protected OnClose getOnClose() {
			return OnClose.NO_OP;
		}

		@Override
		protected void logInfo(Logger log, String message) {
		}

		@Override
		protected void logWarn(Logger log, String message) {
		}

		@Override
		protected void logError(Logger log, String message) {
		}

		@Override
		public void close() {
			this.stop();
		}

	}

}
