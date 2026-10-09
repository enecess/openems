package io.openems.common.jsonrpc.notification;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.GenericJsonrpcNotification;
import io.openems.common.jsonrpc.base.JsonrpcNotification;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Notification for OpenEMS Edge configuration.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "method": "edgeConfig",
 *   "params": {
 *     {@link EdgeConfig#toJson()}
 *   }
 * }
 * </pre>
 */
public class EdgeConfigNotification extends JsonrpcNotification {

	public static record Ordering(UUID sourceId, long revision) {

		public Ordering {
			Objects.requireNonNull(sourceId);
			if (revision < 0) {
				throw new IllegalArgumentException("Revision must not be negative");
			}
		}

		private JsonObject toJson() {
			return JsonUtils.buildJsonObject() //
					.addProperty("sourceId", this.sourceId.toString()) //
					.addProperty("revision", this.revision) //
					.build();
		}
	}

	/**
	 * Parses a {@link JsonObject} to a {@link EdgeConfigNotification}.
	 *
	 * @param j the {@link JsonObject}
	 * @return the {@link EdgeConfigNotification}
	 * @throws OpenemsNamedException on error
	 */
	public static EdgeConfigNotification from(JsonObject j) throws OpenemsNamedException {
		return EdgeConfigNotification.from(GenericJsonrpcNotification.from(j));
	}

	/**
	 * Parses a {@link JsonrpcNotification} to a {@link EdgeConfigNotification}.
	 *
	 * @param n the {@link JsonrpcNotification}
	 * @return the {@link EdgeConfigNotification}
	 * @throws OpenemsNamedException on error
	 */
	public static EdgeConfigNotification from(JsonrpcNotification n) throws OpenemsNamedException {
		var params = n.getParams().deepCopy();
		var jOrdering = params.remove("_ordering");
		final Ordering ordering;
		if (jOrdering == null) {
			ordering = null;
		} else {
			var j = JsonUtils.getAsJsonObject(jOrdering);
			var sourceIdString = JsonUtils.getAsString(j, "sourceId");
			var revision = parseRevision(j);
			final UUID sourceId;
			try {
				sourceId = UUID.fromString(sourceIdString);
			} catch (IllegalArgumentException e) {
				throw new OpenemsException("Invalid EdgeConfig ordering sourceId [" + sourceIdString + "]");
			}
			if (revision < 0) {
				throw new OpenemsException("Invalid negative EdgeConfig ordering revision [" + revision + "]");
			}
			ordering = new Ordering(sourceId, revision);
		}
		return new EdgeConfigNotification(EdgeConfig.fromJson(params), ordering);
	}

	private static long parseRevision(JsonObject ordering) throws OpenemsNamedException {
		var revisionElement = ordering.get("revision");
		if (revisionElement == null || !revisionElement.isJsonPrimitive()
				|| !revisionElement.getAsJsonPrimitive().isNumber()) {
			throw new OpenemsException("Invalid EdgeConfig ordering revision [" + revisionElement + "]");
		}

		final long revision;
		try {
			revision = new BigDecimal(revisionElement.getAsString()).longValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			throw new OpenemsException("Invalid EdgeConfig ordering revision [" + revisionElement + "]");
		}
		if (revision < 0) {
			throw new OpenemsException("Invalid negative EdgeConfig ordering revision [" + revision + "]");
		}
		return revision;
	}

	public static final String METHOD = "edgeConfig";

	private final EdgeConfig config;
	private final Ordering ordering;

	public EdgeConfigNotification(EdgeConfig config) {
		this(config, null);
	}

	public EdgeConfigNotification(EdgeConfig config, Ordering ordering) {
		super(EdgeConfigNotification.METHOD);
		this.config = config;
		this.ordering = ordering;
	}

	@Override
	public JsonObject getParams() {
		var result = this.config.toJson().deepCopy();
		if (this.ordering != null) {
			result.add("_ordering", this.ordering.toJson());
		}
		return result;
	}

	public EdgeConfig getConfig() {
		return this.config;
	}

	public Optional<Ordering> getOrdering() {
		return Optional.ofNullable(this.ordering);
	}

}
