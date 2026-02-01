#version 300 es

layout(location = 0) in vec2 aPhaseSide; // [Phase, Side]

uniform float uGlobalRotation;
uniform float uAspectRatio;
uniform float uGlobalScale;
uniform float uFillMode;
uniform float uLayerOffset;
uniform float uLayerScale;

// Mandala Formula Uniforms
uniform float uL1, uL2, uL3, uL4;
uniform float uA, uB, uC, uD;

// Expansion Uniforms
uniform float uThickness;
const float dP = 1.0 / 2048.0;

out float vPhase;
out vec2 vRawPos;

vec2 getPos(float p) {
    float t = p * 6.28318530718;
    float x = uL1 * cos(t * uA) + uL2 * cos(t * uB) + uL3 * cos(t * uC) + uL4 * cos(t * uD);
    float y = uL1 * sin(t * uA) + uL2 * sin(t * uB) + uL3 * sin(t * uC) + uL4 * sin(t * uD);
    return vec2(x, y);
}

void main() {
    if (uFillMode > 0.5) {
        vPhase = 0.0;
        vRawPos = vec2(0.0);
        float x = float((gl_VertexID % 2) * 2 - 1);
        float y = float((gl_VertexID / 2) * 2 - 1);
        gl_Position = vec4(x, y, 0.0, 1.0);
        return;
    }

    float basePhase = aPhaseSide.x;
    float side = aPhaseSide.y;
    
    // REMOVED fract() here. 
    // Allowing vPhase to be continuous (e.g., 0.99 -> 1.0) prevents the "rainbow" interpolation jump.
    // The fragment shader's fract() will handle the actual color wrapping.
    vPhase = basePhase + uLayerOffset;

    // Line Expansion Logic (Miter)
    vec2 pPrev = getPos(basePhase - dP);
    vec2 pCurr = getPos(basePhase);
    vec2 pNext = getPos(basePhase + dP);

    vec2 v1 = normalize(pCurr - pPrev);
    vec2 v2 = normalize(pNext - pCurr);
    vec2 tangent = normalize(v1 + v2);
    vec2 normal = vec2(-tangent.y, tangent.x);
    
    // Expand along normal based on Thickness knob (scaled to world units)
    vRawPos = (pCurr + normal * side * uThickness * 0.05) * uLayerScale;

    // Global transform
    vec2 pos = vRawPos * uGlobalScale;
    
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
