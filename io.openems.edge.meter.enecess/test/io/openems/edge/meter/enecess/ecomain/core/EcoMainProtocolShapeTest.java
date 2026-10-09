package io.openems.edge.meter.enecess.ecomain.core;

import static io.openems.common.utils.ReflectionUtils.getValueViaReflection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.taskmanager.Priority;

class EcoMainProtocolShapeTest {

	private static final int[][] EXPECTED_BLOCKS = { //
			{ 0, 124 }, //
			{ 124, 124 }, //
			{ 248, 104 }, //
			{ 1000, 88 }, //
			{ 1200, 123 }, //
			{ 1323, 6 }, //
			{ 3000, 10 } };

	@Test
	void definesExactlySevenHighPriorityFc3BlocksWithinTheProtocolLimit() {
		var tasks = new TestableEcoMainCore().protocol().getTaskManager().getTasks();

		assertEquals(EXPECTED_BLOCKS.length, tasks.size());
		for (var i = 0; i < EXPECTED_BLOCKS.length; i++) {
			var task = assertInstanceOf(FC3ReadRegistersTask.class, tasks.get(i));
			assertEquals(EXPECTED_BLOCKS[i][0], task.getStartAddress());
			assertEquals(EXPECTED_BLOCKS[i][1], task.getLength());
			assertTrue(task.getLength() <= 125);
			assertEquals(Priority.HIGH, task.getPriority());
		}
	}

	@Test
	void usesLeastSignificantWordFirstForEveryNumericMultiwordElement() {
		var elements = new TestableEcoMainCore().protocol().getTaskManager().getTasks().stream() //
				.flatMap(task -> java.util.Arrays.stream(task.getElements())) //
				.filter(element -> element instanceof SignedDoublewordElement
						|| element instanceof UnsignedQuadruplewordElement) //
				.toList();

		assertEquals(132, elements.size());
		for (var element : elements) {
			assertEquals(WordOrder.LSWMSW, getValueViaReflection(element, "wordOrder"));
		}
	}

	private static final class TestableEcoMainCore extends EcoMainCoreImpl {

		private ModbusProtocol protocol() {
			return super.defineModbusProtocol();
		}
	}
}
