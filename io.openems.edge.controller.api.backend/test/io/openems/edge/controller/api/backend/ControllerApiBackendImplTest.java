package io.openems.edge.controller.api.backend;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.Proxy.Type;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;
import org.osgi.service.event.Event;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.jsonrpc.notification.EdgeConfigNotification;
import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.EdgeConfig;
import io.openems.common.websocket.DummyWebsocketServer;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyCycle;

public class ControllerApiBackendImplTest {

	@Test
	public void test() throws Exception {

		var edgeConfigs = new LinkedBlockingQueue<EdgeConfigNotification>();
		try (final var server = DummyWebsocketServer.create() //
				.onNotification((ws, notification) -> {
					if (EdgeConfigNotification.METHOD.equals(notification.getMethod())) {
						edgeConfigs.add(EdgeConfigNotification.from(notification));
					}
				}) //
				.build()) {
			server.start();

			// block until Port is not anymore zero
			int port;
			do {
				Thread.sleep(500);
				port = server.getPort();
			} while (port == 0);

			final var clock = new TimeLeapClock(
					Instant.ofEpochSecond(1577836800L) /* starts at 1. January 2020 00:00:00 */, ZoneOffset.UTC);
			final var sut = new ControllerApiBackendImpl();
			new ComponentTest(sut) //
					.addReference("componentManager", new DummyComponentManager(clock)) //
					.addReference("cycle", new DummyCycle(1000)) //
					.addReference("resendHistoricDataWorkerFactory", new DummyResendHistoricDataWorkerFactory()) //
					.addReference("requestHandlerFactory", new DummyBackendOnRequestFactory()) //
					.addReference("oem", new DummyOpenemsEdgeOem()) //
					.addComponent(new DummySum()) //
					.activate(MyConfig.create() //
							.setId("ctrl0") //
							.setUri("ws://localhost:" + port) //
							.setApikey("12345") //
							.setProxyType(Type.DIRECT) //
							.setProxyAddress("") //
							.setPersistencePriority(PersistencePriority.HIGH) //
							.setAggregationPriority(PersistencePriority.VERY_LOW) //
							.setResendPriority(PersistencePriority.MEDIUM) //
							.build());

			var initial = edgeConfigs.poll(5, SECONDS);
			assertNotNull(initial);
			var initialOrdering = initial.getOrdering().orElseThrow();
			assertEquals(0L, initialOrdering.revision());

			new OnOpen(sut).apply(null, null);
			var reconnected = edgeConfigs.poll(5, SECONDS);
			assertNotNull(reconnected);
			var reconnectedOrdering = reconnected.getOrdering().orElseThrow();
			assertEquals(initialOrdering.sourceId(), reconnectedOrdering.sourceId());
			assertEquals(1L, reconnectedOrdering.revision());

			sut.handleEvent(new Event(EdgeEventConstants.TOPIC_CONFIG_UPDATE,
					Map.of(EdgeEventConstants.TOPIC_CONFIG_UPDATE_KEY, EdgeConfig.empty())));
			var updated = edgeConfigs.poll(5, SECONDS);
			assertNotNull(updated);
			var updatedOrdering = updated.getOrdering().orElseThrow();
			assertEquals(initialOrdering.sourceId(), updatedOrdering.sourceId());
			assertEquals(2L, updatedOrdering.revision());

			// Stop connection
			sut.deactivate();
			server.stop();
		}
	}

}
