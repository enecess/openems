package io.openems.edge.meter.enecess.ecomain.core;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;

final class EcoMainTestRegisters {

	private static final int REGISTER_COUNT = 3010;

	private final int[] registers = new int[REGISTER_COUNT];

	EcoMainTestRegisters putInt32Lsw(int address, int rawCentiWatts) {
		this.registers[address] = rawCentiWatts & 0xFFFF;
		this.registers[address + 1] = rawCentiWatts >>> 16 & 0xFFFF;
		return this;
	}

	EcoMainTestRegisters putUInt64Lsw(int address, long rawMilliWattHours) {
		for (var i = 0; i < Long.BYTES / Short.BYTES; i++) {
			this.registers[address + i] = (int) (rawMilliWattHours >>> (Short.SIZE * i)) & 0xFFFF;
		}
		return this;
	}

	EcoMainTestRegisters putUInt16(int address, int rawCentiVoltsOrAmps) {
		this.registers[address] = rawCentiVoltsOrAmps & 0xFFFF;
		return this;
	}

	EcoMainTestRegisters putIdentity(String model, String serialNumber, int softwareVersion) {
		this.putAsciiWordsLsw(3000, model, 4);
		this.putAsciiWordsLsw(3002, serialNumber, 12);
		this.putUInt16(3009, softwareVersion);
		return this;
	}

	DummyModbusBridge newBridge() {
		return this.writeTo(new DummyModbusBridge("modbus0"));
	}

	DummyModbusBridge writeTo(DummyModbusBridge bridge) {
		return bridge.withRegisters(0, this.registers);
	}

	private void putAsciiWordsLsw(int address, String value, int expectedLength) {
		if (value.length() != expectedLength) {
			throw new IllegalArgumentException("Expected exactly " + expectedLength + " ASCII characters");
		}
		for (var i = 0; i < value.length(); i += 2) {
			this.registers[address++] = value.charAt(i) | value.charAt(i + 1) << Byte.SIZE;
		}
	}
}
