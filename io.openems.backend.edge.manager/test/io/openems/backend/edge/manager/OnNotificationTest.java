package io.openems.backend.edge.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.java_websocket.WebSocket;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import io.openems.backend.common.metadata.Edge;
import io.openems.common.jsonrpc.notification.EdgeConfigNotification;
import io.openems.common.jsonrpc.notification.EdgeConfigNotification.Ordering;
import io.openems.common.jsonrpc.notification.EdgeRpcNotification;
import io.openems.common.types.EdgeConfig;

public class OnNotificationTest {

	private static final String EXEMPLARS_ENABLED_PROPERTY = "io.prometheus.metrics.exemplarsEnabled";
	private static String previousExemplarsEnabled;

	/**
	 * Disables Prometheus exemplars because the optional tracer is not on this
	 * module's test classpath.
	 */
	@BeforeClass
	public static void disablePrometheusExemplars() {
		previousExemplarsEnabled = System.setProperty(EXEMPLARS_ENABLED_PROPERTY, "false");
	}

	/**
	 * Restores the Prometheus exemplars system property after this test class.
	 */
	@AfterClass
	public static void restorePrometheusExemplars() {
		if (previousExemplarsEnabled == null) {
			System.clearProperty(EXEMPLARS_ENABLED_PROPERTY);
		} else {
			System.setProperty(EXEMPLARS_ENABLED_PROPERTY, previousExemplarsEnabled);
		}
	}

	@Test
	public void shouldApplyEveryLegacyConfig() throws Exception {
		var fixture = new Fixture();
		var message = new EdgeRpcNotification("edge0", new EdgeConfigNotification(EdgeConfig.empty()));

		fixture.sut.accept(fixture.ws, message);
		fixture.sut.accept(fixture.ws, message);
		verify(fixture.eventAdmin, times(2)).sendEvent(any(Event.class));
	}

	@Test
	public void shouldApplyDuplicateVersionOnlyOnce() throws Exception {
		var fixture = new Fixture();
		var ordering = new Ordering(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 1L);
		var message = new EdgeRpcNotification("edge0", new EdgeConfigNotification(EdgeConfig.empty(), ordering));

		fixture.sut.accept(fixture.ws, message);
		fixture.sut.accept(fixture.ws, message);
		verify(fixture.eventAdmin, times(1)).sendEvent(any(Event.class));
	}

	private static final class Fixture {
		private final WebSocket ws = mock(WebSocket.class);
		private final EventAdmin eventAdmin = mock(EventAdmin.class);
		private final Edge edge = mock(Edge.class);
		private final OnNotification sut = new OnNotification("test", //
				() -> this.eventAdmin, //
				() -> null, //
				() -> null, //
				edgeId -> Optional.of(this.edge), //
				(edgeId, message) -> {
				}, //
				(log, message) -> {
				}, //
				(log, message) -> {
				});

		private Fixture() {
			when(this.ws.getAttachment()).thenReturn(new WsData(this.ws));
		}
	}
}
