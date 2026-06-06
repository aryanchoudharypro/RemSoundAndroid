package rem.receiver.android

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmSubHeader(
	val frameId: Long,
	val partIndex: Int,
	val totalParts: Int
)

object RemPcmFrame {
	const val SUB_HEADER_SIZE = 6

	fun tryReadSubHeader(source: ByteArray, offset: Int, length: Int): PcmSubHeader? {
		if (length < SUB_HEADER_SIZE) return null
		val buffer = ByteBuffer.wrap(source, offset, SUB_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
		val frameId = buffer.int.toLong() and 0xFFFFFFFFL
		val partIndex = buffer.get().toInt() and 0xFF
		val totalParts = buffer.get().toInt() and 0xFF
		if (totalParts == 0 || partIndex >= totalParts) return null
		return PcmSubHeader(frameId, partIndex, totalParts)
	}
}

class PcmFrameAssembler {
	private val TAG = "PcmFrameAssembler"
	private var pendingFrameId: Long = 0
	private var pendingPartIndex: Int = 0
	private var pendingTotalParts: Int = 0
	private val assemblyBuffer = ByteArray(8192)
	private var assemblyWritten: Int = 0

	fun tryAssemble(partBytes: ByteArray, offset: Int, length: Int, header: PcmSubHeader): ByteArray? {
		if (header.totalParts == 0) return null
		if (header.partIndex == 0) {
			pendingFrameId = header.frameId
			pendingPartIndex = 0
			pendingTotalParts = header.totalParts
			assemblyWritten = 0
		} else if (header.frameId != pendingFrameId || header.partIndex != pendingPartIndex || header.totalParts != pendingTotalParts) {
			assemblyWritten = 0
			pendingTotalParts = 0
			return null
		}

		if (assemblyWritten + length > assemblyBuffer.size) {
			assemblyWritten = 0
			pendingTotalParts = 0
			return null
		}

		System.arraycopy(partBytes, offset, assemblyBuffer, assemblyWritten, length)
		assemblyWritten += length
		pendingPartIndex++

		if (pendingPartIndex == pendingTotalParts) {
			val assembled = ByteArray(assemblyWritten)
			System.arraycopy(assemblyBuffer, 0, assembled, 0, assemblyWritten)
			pendingTotalParts = 0
			assemblyWritten = 0
			return assembled
		}
		return null
	}
}
