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
            // If no main context exists yet, create it without sharing
            if (SharedContextManager.mainContext == null) {
                val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
                if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                    Log.e("SharedEGL", "Failed to create PRIMARY context. Error: ${egl.eglGetError()}")
                    // Still return it - GLSurfaceView will handle the error
                    return context ?: EGL10.EGL_NO_CONTEXT
                }
                SharedContextManager.mainContext = context
                SharedContextManager.refCount++
                Log.d("SharedEGL", "Primary context established for sharing (ID: $context)")
                return context
            }
            
            // Create a shared context from the main context
            val mainCtx = SharedContextManager.mainContext!!
            Log.d("SharedEGL", "Attempting to create shared context from main (ID: $mainCtx)")
            val context = egl.eglCreateContext(display, config, mainCtx, attribList)
            
            if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                val error = egl.eglGetError()
                Log.e("SharedEGL", "Failed to create SHARED context. Error: $error (0x${error.toString(16)})")
                Log.e("SharedEGL", "Main context ID: $mainCtx, Display: $display")
                // Try one more time without sharing as a fallback
                val fallbackCtx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
                Log.w("SharedEGL", "Created fallback unshared context (textures won't be shared!)")
                return fallbackCtx ?: EGL10.EGL_NO_CONTEXT
            }
            
            SharedContextManager.refCount++
            Log.d("SharedEGL", "Shared context created successfully (ID: $context, refCount: ${SharedContextManager.refCount})")
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
