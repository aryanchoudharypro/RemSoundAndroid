package rem.receiver.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import rem.receiver.android.theme.RemSoundAndroidTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			RemSoundAndroidTheme {
				MainScreen()
			}
		}
	}
}
