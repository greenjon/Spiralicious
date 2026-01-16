package llm.slop.spirals

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.*

object SharedContextManager {
    @Volatile
    var mainContext: EGLContext? = null
    var refCount = 0
}

class SharedEGLContextFactory : GLSurfaceView.EGLContextFactory {
    
    private val EGL_CONTEXT_CLIENT_VERSION = 0x3098

    override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
        val attribList = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
        
        synchronized(SharedContextManager) {
            val shareContext = SharedContextManager.mainContext ?: EGL10.EGL_NO_CONTEXT
            val context = egl.eglCreateContext(display, config, shareContext, attribList)
            
            if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                Log.e("SharedEGL", "Failed to create context. Error: ${egl.eglGetError()}")
                return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList) ?: EGL10.EGL_NO_CONTEXT
            }

            if (SharedContextManager.mainContext == null) {
                SharedContextManager.mainContext = context
                Log.d("SharedEGL", "Primary context established for sharing")
            }
            SharedContextManager.refCount++
            return context
        }
    }

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        synchronized(SharedContextManager) {
            SharedContextManager.refCount--
            if (context == SharedContextManager.mainContext && SharedContextManager.refCount > 0) {
                Log.d("SharedEGL", "Primary context preserved (refCount: ${SharedContextManager.refCount})")
                return 
            }
            if (context == SharedContextManager.mainContext) {
                SharedContextManager.mainContext = null
                Log.d("SharedEGL", "Primary context destroyed")
            }
            egl.eglDestroyContext(display, context)
        }
    }
}
