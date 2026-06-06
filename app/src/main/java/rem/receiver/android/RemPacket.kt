package rem.receiver.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
enum class RemPacketType(val value: Byte) {
	Format(1),
	Audio(2),
	KeepAlive(3),
	Heartbeat(4),
	Control(5);
	companion object {
		fun fromValue(value: Byte): RemPacketType? = entries.find { it.value == value }
	}
}

object RemoteControlKind {
	const val VolumeUp: Byte = 0
	const val VolumeDown: Byte = 1
	const val MuteToggle: Byte = 2
	const val SystemVolumeUp: Byte = 3
	const val SystemVolumeDown: Byte = 4
	const val SystemMuteToggle: Byte = 5
}

enum class HeartbeatType(val value: Byte) {
	Ping(0),
	Pong(1);
	companion object {
		fun fromValue(value: Byte): HeartbeatType? = entries.find { it.value == value }
	}
}

data class RemHeader(
	val type: RemPacketType,
	val streamId: Int,
	val sequence: Long
)

data class AudioFormatInfo(
	val sampleRate: Int,
	val channels: Int,
	val bitsPerSample: Int,
	val encoding: Int,
	val blockAlign: Int,
	val averageBytesPerSecond: Int,
	val codec: Int,
	val frameSamplesPerChannel: Int,
	val lane: Byte
)

object RemPacket {
	const val MAGIC = 0x444E4D52
	const val VERSION: Byte = 1
	const val HEADER_SIZE = 12
	const val FORMAT_PAYLOAD_SIZE = 32
	const val FORMAT_PAYLOAD_EXTENDED_SIZE = 36
	fun tryReadHeader(packet: ByteArray, length: Int): RemHeader? {
		if (length < HEADER_SIZE) return null
		val buffer = ByteBuffer.wrap(packet, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
		val magic = buffer.int
		if (magic != MAGIC) return null
		val version = buffer.get()
		if (version != VERSION) return null
		val typeByte = buffer.get()
		val type = RemPacketType.fromValue(typeByte) ?: return null
		var streamId = buffer.short.toInt() and 0xFFFF
		if (streamId == 0) streamId = 1
		val sequence = buffer.int.toLong() and 0xFFFFFFFFL
		return RemHeader(type, streamId, sequence)
	}
	fun isValidHeader(packet: ByteArray, length: Int): Boolean {
		return tryReadHeader(packet, length) != null
	}
	fun tryReadFormat(packet: ByteArray, offset: Int, length: Int): AudioFormatInfo? {
		if (length < FORMAT_PAYLOAD_SIZE) return null
		val buffer = ByteBuffer.wrap(packet, offset, length).order(ByteOrder.LITTLE_ENDIAN)
		val sampleRate = buffer.int
		val channels = buffer.int
		val bitsPerSample = buffer.int
		val encoding = buffer.int
		val blockAlign = buffer.int
		val averageBytesPerSecond = buffer.int
		val codec = buffer.int
		val frameSamplesPerChannel = buffer.int
		var lane: Byte = 0
		if (length >= FORMAT_PAYLOAD_EXTENDED_SIZE) {
			lane = buffer.get()
		}
		return AudioFormatInfo(
			sampleRate, channels, bitsPerSample, encoding, blockAlign,
			averageBytesPerSecond, codec, frameSamplesPerChannel, lane
		)
	}
	private fun writeHeader(buffer: ByteBuffer, type: Byte, streamId: Short, sequence: Int) {
		buffer.putInt(MAGIC)
		buffer.put(VERSION)
		buffer.put(type)
		buffer.putShort(streamId)
		buffer.putInt(sequence)
	}
	fun tryReadHeartbeatPayload(packet: ByteArray, offset: Int, length: Int): Pair<Byte, Long>? {
		if (length < 9) return null
		val buffer = ByteBuffer.wrap(packet, offset, length).order(ByteOrder.LITTLE_ENDIAN)
		val kind = buffer.get()
		val timestamp = buffer.long
		return Pair(kind, timestamp)
	}
	fun buildHeartbeatPacket(sequence: Int, timestamp: Long): ByteArray {
		val payloadSize = 9
		val buffer = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
		writeHeader(buffer, RemPacketType.Heartbeat.value, 1, sequence)
		buffer.put(HeartbeatType.Ping.value)
		buffer.putLong(timestamp)
		return buffer.array()
	}
	fun buildHeartbeatPongPacket(sequence: Int, timestamp: Long): ByteArray {
		val payloadSize = 9
		val buffer = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
		writeHeader(buffer, RemPacketType.Heartbeat.value, 1, sequence)
		buffer.put(HeartbeatType.Pong.value)
		buffer.putLong(timestamp)
		return buffer.array()
	}
	fun buildControlPacket(sequence: Int, kind: Byte, delta: Byte): ByteArray {
		val payloadSize = 2
		val buffer = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
		writeHeader(buffer, RemPacketType.Control.value, 1, sequence)
		buffer.put(kind)
		buffer.put(delta)
		return buffer.array()
	}
}
