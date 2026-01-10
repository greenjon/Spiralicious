#version 300 es

layout(location = 0) in vec3 aPosPhase; // [X, Y, Phase]

uniform float uGlobalRotation;
uniform float uAspectRatio;
uniform float uGlobalScale;
uniform float uFillMode;

out float vPhase;

void main() {
    if (uFillMode > 0.5) {
        vPhase = 0.0;
        // Fullscreen quad using gl_VertexID (0 to 3)
        // 0: (-1,-1), 1: (1,-1), 2: (-1,1), 3: (1,1)
        float x = float((gl_VertexID % 2) * 2 - 1);
        float y = float((gl_VertexID / 2) * 2 - 1);
        gl_Position = vec4(x, y, 0.0, 1.0);
        return;
    }

    vPhase = aPosPhase.z;

    // Geometry is pre-calculated on CPU in normalized -1..1 range
    vec2 pos = aPosPhase.xy * uGlobalScale;
    
    float cosR = cos(uGlobalRotation);
    float sinR = sin(uGlobalRotation);
    
    float rotX = pos.x * cosR - pos.y * sinR;
    float rotY = pos.x * sinR + pos.y * cosR;

    if (uAspectRatio > 1.0) {
        rotX /= uAspectRatio;
    } else {
        rotY *= uAspectRatio;
    }

    gl_Position = vec4(rotX, rotY, 0.0, 1.0);
}
