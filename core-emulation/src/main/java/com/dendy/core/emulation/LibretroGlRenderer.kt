package com.dendy.core.emulation

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.dendy.core.model.VirtualButton
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal interface FrameProvider {
    fun frameWidth(): Int
    fun frameHeight(): Int
    fun copyFrame(target: ByteBuffer): Boolean
}

internal class LibretroGlRendererHost(
    private val frameProvider: FrameProvider,
    private val onHardwareInput: (VirtualButton, Boolean) -> Unit,
) : RendererHost {
    private var frameWidth: Int = 256
    private var frameHeight: Int = 240
    private var view: LibretroSurfaceView? = null

    override fun createView(context: Context): View {
        return view ?: LibretroSurfaceView(
            context = context,
            renderer = LibretroFrameRenderer(frameProvider),
            onHardwareInput = onHardwareInput,
        ).also { created ->
            created.id = ViewCompat.generateViewId()
            created.isFocusable = true
            created.isFocusableInTouchMode = true
            view = created
        }
    }

    override fun resume() {
        view?.onResume()
    }

    override fun pause() {
        view?.onPause()
    }

    override fun release() {
        pause()
        view = null
    }

    fun updateFrameInfo(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
        view?.renderer?.updateFrameInfo(width, height)
    }

    fun requestFrame() {
        view?.requestRender()
    }
}

private class LibretroSurfaceView(
    context: Context,
    val renderer: LibretroFrameRenderer,
    private val onHardwareInput: (VirtualButton, Boolean) -> Unit,
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mapKey(keyCode)?.let {
            onHardwareInput(it, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        mapKey(keyCode)?.let {
            onHardwareInput(it, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return super.onGenericMotionEvent(event)
    }

    private fun mapKey(keyCode: Int): VirtualButton? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_Z,
            -> VirtualButton.A
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_X,
            -> VirtualButton.B
            KeyEvent.KEYCODE_DPAD_UP -> VirtualButton.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> VirtualButton.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> VirtualButton.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> VirtualButton.RIGHT
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_START,
            -> VirtualButton.START
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            -> VirtualButton.SELECT
            else -> null
        }
    }
}

private class LibretroFrameRenderer(
    private val frameProvider: FrameProvider,
) : GLSurfaceView.Renderer {
    private var program = 0
    private var textureId = 0
    private var textureWidth = 0
    private var textureHeight = 0
    private var frameBuffer: ByteBuffer? = null
    private val vertices: FloatBuffer = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,
    )
    private val texCoords: FloatBuffer = floatBufferOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f,
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createTexture()
        textureWidth = 0
        textureHeight = 0
        frameBuffer = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val width = frameProvider.frameWidth()
        val height = frameProvider.frameHeight()
        if (width <= 0 || height <= 0) {
            return
        }
        ensureFrameBuffer(width, height)
        val buffer = frameBuffer ?: return
        if (!frameProvider.copyFrame(buffer)) {
            return
        }
        buffer.position(0)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        uploadFrame(buffer, width, height)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")

        vertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(positionHandle)

        texCoords.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun updateFrameInfo(width: Int, height: Int) {
        ensureFrameBuffer(width, height)
    }

    private fun ensureFrameBuffer(width: Int, height: Int) {
        val expectedBytes = width * height * 4
        if (frameBuffer == null || frameBuffer?.capacity() != expectedBytes) {
            frameBuffer = ByteBuffer.allocateDirect(expectedBytes).order(ByteOrder.nativeOrder())
        }
    }

    private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        return GLES20.glCreateProgram().also { programId ->
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shaderId ->
            GLES20.glShaderSource(shaderId, source)
            GLES20.glCompileShader(shaderId)
        }
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun uploadFrame(buffer: ByteBuffer, width: Int, height: Int) {
        if (width != textureWidth || height != textureHeight) {
            allocateTexture(buffer, width, height)
            return
        }

        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            width,
            height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer,
        )

        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            // Some emulators recreate the texture object while our cached size survives.
            allocateTexture(buffer, width, height)
        }
    }

    private fun allocateTexture(buffer: ByteBuffer, width: Int, height: Int) {
        buffer.position(0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer,
        )
        textureWidth = width
        textureHeight = height
        GLES20.glGetError()
    }

    private fun floatBufferOf(vararg values: Float): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
