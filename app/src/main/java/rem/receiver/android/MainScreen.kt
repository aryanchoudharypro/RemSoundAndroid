package rem.receiver.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*

@Composable
fun MainScreen(
	modifier: Modifier = Modifier
) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val prefs = context.getSharedPreferences("remsound_prefs", android.content.Context.MODE_PRIVATE)
	var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
	var targetIp by remember { mutableStateOf("") }
	var volume by remember { mutableFloatStateOf(prefs.getFloat("volume", 100f)) }
	var bufferMs by remember { mutableFloatStateOf(prefs.getInt("buffer_ms", 50).toFloat()) }
	val isReceiving by RemSoundService.isRunning.collectAsState()
	var instanceId = prefs.getString("instance_id", null)
	if (instanceId == null) {
		instanceId = java.util.UUID.randomUUID().toString()
		prefs.edit().putString("instance_id", instanceId).apply()
	}
	Column(modifier = modifier.padding(16.dp).fillMaxSize()) {
		Text("RemSound Android Receiver", style = MaterialTheme.typography.headlineMedium)
		Spacer(modifier = Modifier.height(16.dp))
		OutlinedTextField(
			value = password,
			onValueChange = { password = it },
			label = { Text("Profile Password") },
			modifier = Modifier.fillMaxWidth()
		)
		Spacer(modifier = Modifier.height(16.dp))
		OutlinedTextField(
			value = targetIp,
			onValueChange = { targetIp = it },
			label = { Text("Sender/Relay IP (optional)") },
			modifier = Modifier.fillMaxWidth()
		)
		Spacer(modifier = Modifier.height(16.dp))
		Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
			contentDescription = "Volume"
			stateDescription = "${volume.toInt()}%"
			progressBarRangeInfo = ProgressBarRangeInfo(
				current = volume,
				range = 0f..200f
			)
			setProgress { newValue ->
				volume = newValue
				val intent = android.content.Intent(context, RemSoundService::class.java)
				intent.action = RemSoundService.ACTION_SET_VOLUME
				intent.putExtra(RemSoundService.EXTRA_VOLUME, newValue)
				context.startService(intent)
				true
			}
		}) {
			Text("Volume: ${volume.toInt()}%")
			Slider(
				value = volume,
				onValueChange = { 
					volume = it 
					val intent = android.content.Intent(context, RemSoundService::class.java)
					intent.action = RemSoundService.ACTION_SET_VOLUME
					intent.putExtra(RemSoundService.EXTRA_VOLUME, it)
					context.startService(intent)
				},
				valueRange = 0f..200f
			)
		}
		Spacer(modifier = Modifier.height(16.dp))
		Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
			contentDescription = "Buffer"
			stateDescription = "${bufferMs.toInt()}ms"
			progressBarRangeInfo = ProgressBarRangeInfo(
				current = bufferMs,
				range = 10f..500f
			)
			setProgress { newValue ->
				bufferMs = newValue
				true
			}
		}) {
			Text("Buffer: ${bufferMs.toInt()}ms")
			Slider(
				value = bufferMs,
				onValueChange = { bufferMs = it },
				valueRange = 10f..500f
			)
		}
		Spacer(modifier = Modifier.height(16.dp))
		Button(
			onClick = {
				if (isReceiving) {
					val intent = android.content.Intent(context, RemSoundService::class.java)
					intent.action = RemSoundService.ACTION_STOP
					context.startService(intent)
				} else {
					prefs.edit()
						.putString("password", password)
						.putFloat("volume", volume)
						.putInt("buffer_ms", bufferMs.toInt())
						.apply()
					val intent = android.content.Intent(context, RemSoundService::class.java)
					intent.action = RemSoundService.ACTION_START
					intent.putExtra(RemSoundService.EXTRA_PASSWORD, password)
					intent.putExtra(RemSoundService.EXTRA_INSTANCE_ID, instanceId)
					intent.putExtra(RemSoundService.EXTRA_TARGET_IP, targetIp)
					intent.putExtra(RemSoundService.EXTRA_VOLUME, volume)
					intent.putExtra(RemSoundService.EXTRA_BUFFER_MS, bufferMs.toInt())
					androidx.core.content.ContextCompat.startForegroundService(context, intent)
				}
			},
			modifier = Modifier.fillMaxWidth()
		) {
			Text(if (isReceiving) "Stop Receiving" else "Start Receiving")
		}
		Spacer(modifier = Modifier.height(32.dp))
	}
}
