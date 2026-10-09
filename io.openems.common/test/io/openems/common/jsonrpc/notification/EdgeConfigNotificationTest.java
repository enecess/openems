package io.openems.common.jsonrpc.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.EdgeConfig;

public class EdgeConfigNotificationTest {

	private static JsonObject withRevision(JsonElement revision) {
		var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		var json = new EdgeConfigNotification(EdgeConfig.empty(),
				new EdgeConfigNotification.Ordering(sourceId, 1L)).toJsonObject();
		json.getAsJsonObject("params").getAsJsonObject("_ordering").add("revision", revision);
		return json;
	}

	@Test
	public void shouldKeepLegacyFormatWithoutOrdering() throws Exception {
		var parsed = EdgeConfigNotification.from(new EdgeConfigNotification(EdgeConfig.empty()).toJsonObject());

		assertTrue(parsed.getOrdering().isEmpty());
		assertFalse(parsed.getParams().has("_ordering"));
	}

	@Test
	public void shouldRoundTripOrderingAndKeepConfigClean() throws Exception {
		var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		var ordering = new EdgeConfigNotification.Ordering(sourceId, 12L);
		var json = new EdgeConfigNotification(EdgeConfig.empty(), ordering).toJsonObject();

		assertTrue(json.getAsJsonObject("params").has("_ordering"));
		var parsed = EdgeConfigNotification.from(json);
		assertEquals(ordering, parsed.getOrdering().orElseThrow());
		assertFalse(parsed.getConfig().toJson().has("_ordering"));
	}

	@Test
	public void shouldRejectInvalidOrdering() {
		var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		var invalidSourceIdJson = new EdgeConfigNotification(EdgeConfig.empty(),
				new EdgeConfigNotification.Ordering(sourceId, 1L)).toJsonObject();

		invalidSourceIdJson.getAsJsonObject("params").getAsJsonObject("_ordering")
				.addProperty("sourceId", "not-a-uuid");
		assertThrows(OpenemsNamedException.class, () -> EdgeConfigNotification.from(invalidSourceIdJson));

		var invalidRevisionJson = new EdgeConfigNotification(EdgeConfig.empty(),
				new EdgeConfigNotification.Ordering(sourceId, 1L)).toJsonObject();
		invalidRevisionJson.getAsJsonObject("params").getAsJsonObject("_ordering")
				.addProperty("revision", -1L);
		assertThrows(OpenemsNamedException.class, () -> EdgeConfigNotification.from(invalidRevisionJson));
	}

	@Test
	public void shouldRejectNonIntegralRevision() {
		var json = withRevision(new JsonPrimitive(new BigDecimal("1.5")));

		assertThrows(OpenemsNamedException.class, () -> EdgeConfigNotification.from(json));
	}

	@Test
	public void shouldRejectRevisionOutsideLongRange() {
		var json = withRevision(new JsonPrimitive(new BigDecimal("18446744073709551616")));

		assertThrows(OpenemsNamedException.class, () -> EdgeConfigNotification.from(json));
	}

	@Test
	public void shouldRejectStringRevision() {
		var json = withRevision(new JsonPrimitive("1"));

		assertThrows(OpenemsNamedException.class, () -> EdgeConfigNotification.from(json));
	}

	@Test
	public void shouldAcceptMaximumLongRevision() throws Exception {
		var parsed = EdgeConfigNotification.from(withRevision(new JsonPrimitive(Long.MAX_VALUE)));

		assertEquals(Long.MAX_VALUE, parsed.getOrdering().orElseThrow().revision());
	}
}
