#version 300 es

layout(location = 0) in float u;
layout(location = 1) in float side; // -1.0 or 1.0

uniform vec4 uOmega; 
uniform vec4 uL;     
uniform vec4 uPhi;   
uniform float uT;
uniform float uGlobalRotation;
uniform float uAspectRatio;
uniform float uThickness;
uniform float uGlobalScale;
uniform float uFillMode; // 0.0 = Mandala, 1.0 = Fullscreen Fill

vec2 getPos(float uVal) {
    float t = uVal * uT;
    float x = uL.x * cos(uOmega.x * t + uPhi.x) +
              uL.y * cos(uOmega.y * t + uPhi.y) +
              uL.z * cos(uOmega.z * t + uPhi.z) +
              uL.w * cos(uOmega.w * t + uPhi.w); 

    float y = uL.x * sin(uOmega.x * t + uPhi.x) +
              uL.y * sin(uOmega.y * t + uPhi.y) +
              uL.z * sin(uOmega.z * t + uPhi.z) +
              uL.w * sin(uOmega.w * t + uPhi.w); 
    return vec2(x * uGlobalScale, y * uGlobalScale);
}

void main() {
    if (uFillMode > 0.5) {
        // Simple quad fill using the existing attributes
        gl_Position = vec4(u * 2.0 - 1.0, side, 0.0, 1.0);
        return;
    }

    vec2 pos = getPos(u);
    
    float eps = 0.0001;
    vec2 posNext = getPos(u + eps);
    vec2 tangent = normalize(posNext - pos);
    vec2 normal = vec2(-tangent.y, tangent.x);

    // Thickness also scaled by global scale to maintain proportion
    vec2 finalPos = pos + normal * uThickness * side * uGlobalScale;

    float cosR = cos(uGlobalRotation);
    float sinR = sin(uGlobalRotation);
    
    float rotX = finalPos.x * cosR - finalPos.y * sinR;
    float rotY = finalPos.x * sinR + finalPos.y * cosR;

    if (uAspectRatio > 1.0) {
        rotX /= uAspectRatio;
    } else {
        rotY *= uAspectRatio;
    }

    // Centered at (0,0) for the new 16:9 preview window
    gl_Position = vec4(rotX, rotY, 0.0, 1.0);
}
