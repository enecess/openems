package io.openems.edge.meter.enecess.ecomain.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

public class EcoMainSnapshotTest {

	@Test
	public void testDefensivelyCopiesBranchReadings() {
		final var address = new EcoMainChannelAddress(0, 1);
		final var phase = new EcoMainPhaseReading(1, 2, 3, 4L, 5L);
		final var branchReadings = new HashMap<EcoMainChannelAddress, EcoMainPhaseReading>();
		branchReadings.put(address, phase);

		final var snapshot = new EcoMainSnapshot(1L, EcoMainIdentityStatus.VALID, null, branchReadings);
		branchReadings.clear();

		assertSame(phase, snapshot.branchReadings().get(address));
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.branchReadings().put(new EcoMainChannelAddress(1, 1), phase));
	}

	@Test
	public void testPreservesMissingValues() {
		final var missingPhase = new EcoMainPhaseReading(null, null, null, null, null);
		final var main = new EcoMainMainReading(missingPhase, null, null, null, null, null);
		final var presentAddress = new EcoMainChannelAddress(0, 1);
		final var missingAddress = new EcoMainChannelAddress(0, 2);
		final var snapshot = new EcoMainSnapshot(2L, EcoMainIdentityStatus.UNKNOWN, main,
				java.util.Map.of(presentAddress, missingPhase));

		assertNull(snapshot.main().l1().activePower());
		assertNull(snapshot.main().l1().voltage());
		assertNull(snapshot.main().l1().current());
		assertNull(snapshot.main().l1().forwardEnergy());
		assertNull(snapshot.main().l1().reverseEnergy());
		assertNull(snapshot.main().totalActivePower());
		assertNull(snapshot.main().totalForwardEnergy());
		assertNull(snapshot.main().totalReverseEnergy());
		assertFalse(snapshot.branchReadings().containsKey(missingAddress));
	}

	@Test
	public void testRejectsNullIdentityStatus() {
		assertThrows(NullPointerException.class, () -> new EcoMainSnapshot(3L, null, null, java.util.Map.of()));
	}
}
