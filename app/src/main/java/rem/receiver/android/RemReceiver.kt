package rem.receiver.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import io.github.jaredmdobson.concentus.OpusDecoder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
class RemReceiver(private val password: String, private val instanceId: String) {
	private val TAG = "RemReceiver"
	private var socket: DatagramSocket? = null
	private var audioTrack: AudioTrack? = null
	private var opusDecoder: OpusDecoder? = null
	private val pcmAssembler = PcmFrameAssembler()
	private var key: ByteArray? = null
	@Volatile
	private var formatInfo: AudioFormatInfo? = null
	private var sequence = 0
	private var targetAddress: InetAddress? = null
	private var isRunning = false
	private var volumeMultiplier = 1.0f
	private var bufferMs = 50
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	fun setVolume(vol: Float) {
		volumeMultiplier = vol / 100f
	}
	fun setBufferMs(ms: Int) {
		bufferMs = ms
		val format = formatInfo
		if (format != null) {
			setupAudioTrack(format)
		}
	}
	fun start(targetIp: String = "") {
		if (isRunning) return
		isRunning = true
		try {
			if (targetIp.isNotBlank()) {
				targetAddress = InetAddress.getByName(targetIp)
			}
		} catch (e: Exception) {
			e.printStackTrace()
			targetAddress = null
		}
		scope.launch(Dispatchers.IO) {
			try {
				if (key == null) {
					key = RemCrypto.deriveKey(password)
				}
				socket = DatagramSocket(null)
				socket?.reuseAddress = true
				socket?.bind(InetSocketAddress(47830))
				launch {
					while (isRunning) {
						if (targetAddress != null) {
							sendHeartbeat()
						}
						delay(10000)
					}
				}
				launch {
					while (isRunning) {
						sendDiscoveryBroadcast()
						delay(1500)
					}
				}
				val buffer = ByteArray(2048)
				while (isRunning) {
					val packet = DatagramPacket(buffer, buffer.size)
					socket?.receive(packet)
					if (!RemPacket.isValidHeader(packet.data, packet.length)) continue
					handlePacket(packet.data, packet.length, packet)
				}
			} catch (e: Exception) {
				if (isRunning) {
					e.printStackTrace()
				}
			}
		}
	}
	private fun sendHeartbeat() {
		val addr = targetAddress ?: return
		try {
			val packetData = RemPacket.buildHeartbeatPacket(sequence++, System.currentTimeMillis())
			val packet = DatagramPacket(packetData, packetData.size, addr, 47830)
			socket?.send(packet)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun sendDiscoveryBroadcast() {
		try {
			val json = org.json.JSONObject()
			json.put("InstanceId", instanceId)
			json.put("Name", android.os.Build.MODEL)
			json.put("AudioPort", 47830)
			json.put("CanSend", false)
			json.put("CanReceive", true)
			val bytes = json.toString().toByteArray(Charsets.UTF_8)
			val announcer = DatagramSocket()
			announcer.broadcast = true
			val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), 47831)
			announcer.send(packet)
			announcer.close()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	fun sendControlPacket(kind: Byte, delta: Byte = 0) {
		val addr = targetAddress ?: return
		scope.launch(Dispatchers.IO) {
			try {
				val packetData = RemPacket.buildControlPacket(sequence++, kind, delta)
				val packet = DatagramPacket(packetData, packetData.size, addr, 47830)
				socket?.send(packet)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
	fun stop() {
		isRunning = false
		scope.cancel()
		socket?.close()
		socket = null
		audioTrack?.stop()
		audioTrack?.release()
		audioTrack = null
	}
	private fun handlePacket(data: ByteArray, length: Int, packet: DatagramPacket) {
		if (targetAddress == null && packet.address != null) {
			targetAddress = packet.address
		}
		val header = RemPacket.tryReadHeader(data, length) ?: return
		when (header.type) {
			RemPacketType.Format -> {
				val format = RemPacket.tryReadFormat(data, RemPacket.HEADER_SIZE, length - RemPacket.HEADER_SIZE)
				if (format != null && formatInfo?.codec != format.codec) {
					Log.d(TAG, "Format updated: $format")
					formatInfo = format
					setupAudioTrack(format)
				}
			}
			RemPacketType.Audio -> {
				val currentFormat = formatInfo ?: return
				if (currentFormat.codec == 2) {
					handleOpus(data, RemPacket.HEADER_SIZE, length - RemPacket.HEADER_SIZE, currentFormat)
				} else if (currentFormat.codec == 1) {
					handlePcm(data, RemPacket.HEADER_SIZE, length - RemPacket.HEADER_SIZE, currentFormat)
				}
			}
			RemPacketType.Heartbeat -> {
				val payload = RemPacket.tryReadHeartbeatPayload(data, RemPacket.HEADER_SIZE, length - RemPacket.HEADER_SIZE)
				if (payload != null && payload.first == HeartbeatType.Ping.value) {
					scope.launch(Dispatchers.IO) {
						try {
							val pong = RemPacket.buildHeartbeatPongPacket(sequence++, payload.second)
							val p = DatagramPacket(pong, pong.size, packet.address, packet.port)
							socket?.send(p)
						} catch (e: Exception) {
							e.printStackTrace()
						}
					}
				}
			}
			else -> {}
		}
	}
	private fun handlePcm(data: ByteArray, offset: Int, length: Int, format: AudioFormatInfo) {
		val subHeader = RemPcmFrame.tryReadSubHeader(data, offset, length) ?: return
		val assembled = pcmAssembler.tryAssemble(
			data,
			offset + RemPcmFrame.SUB_HEADER_SIZE,
			length - RemPcmFrame.SUB_HEADER_SIZE,
			subHeader
		) ?: return
		val currentKey = key ?: return
		val plainText = RemCrypto.tryDecrypt(currentKey, assembled, 0, assembled.size) ?: return
		val sampleCount = plainText.size / 3
		val shortOut = ShortArray(sampleCount)
		for (i in 0 until sampleCount) {
			val b0 = plainText[i * 3].toInt() and 0xFF
			val b1 = plainText[i * 3 + 1].toInt() and 0xFF
			val b2 = plainText[i * 3 + 2].toInt()
			var sample = ((b2 shl 8) or b1).toShort().toInt()
			sample = (sample * volumeMultiplier).toInt()
			sample = maxOf(-32768, minOf(32767, sample))
			shortOut[i] = sample.toShort()
		}
		audioTrack?.write(shortOut, 0, sampleCount)
	}
	private fun setupAudioTrack(format: AudioFormatInfo) {
		audioTrack?.release()
		val minBufferSize = AudioTrack.getMinBufferSize(
			format.sampleRate,
			AudioFormat.CHANNEL_OUT_STEREO,
			AudioFormat.ENCODING_PCM_16BIT
		)
		val requestedBytes = (format.sampleRate * format.channels * 2 * bufferMs) / 1000
		val bufferSize = maxOf(minBufferSize, requestedBytes) 
		audioTrack = AudioTrack.Builder()
			.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_MEDIA)
					.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
					.build()
			)
			.setAudioFormat(
				AudioFormat.Builder()
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.setSampleRate(format.sampleRate)
					.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
					.build()
			)
			.setBufferSizeInBytes(bufferSize)
			.setTransferMode(AudioTrack.MODE_STREAM)
			.build()
		audioTrack?.play()
		if (format.codec == 2) {
			opusDecoder = OpusDecoder(format.sampleRate, format.channels)
		}
	}
	private fun handleOpus(data: ByteArray, offset: Int, length: Int, format: AudioFormatInfo) {
		val currentKey = key ?: return
		val plainText = RemCrypto.tryDecrypt(currentKey, data, offset, length) ?: return
		val decoder = opusDecoder ?: return
		val frameSize = Math.max(120, format.frameSamplesPerChannel)
		val shortOut = ShortArray(frameSize * format.channels)
		try {
			val decoded = decoder.decode(plainText, 0, plainText.size, shortOut, 0, frameSize, false)
			if (decoded > 0) {
				for (i in 0 until decoded * format.channels) {
					var sample = shortOut[i].toInt()
					sample = (sample * volumeMultiplier).toInt()
					sample = maxOf(-32768, minOf(32767, sample))
					shortOut[i] = sample.toShort()
				}
				audioTrack?.write(shortOut, 0, decoded * format.channels)
			}
		} catch (e: Exception) {
			Log.e(TAG, "Opus decode failed", e)
		}
	}
}
