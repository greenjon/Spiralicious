package llm.slop.spirals

import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var spiralSurfaceView: SpiralSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        spiralSurfaceView = SpiralSurfaceView(this)
        setContentView(spiralSurfaceView)
    }

    override fun onPause() {
        super.onPause()
        spiralSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        spiralSurfaceView.onResume()
    }
}
