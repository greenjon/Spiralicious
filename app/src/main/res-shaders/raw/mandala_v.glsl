#version 300 es

layout(location = 0) in float u;
layout(location = 1) in float side; // -1.0 or 1.0

uniform vec3 uOmega;
uniform vec3 uL;
uniform vec3 uPhi;
uniform float uT;
uniform float uGlobalRotation;
uniform float uAspectRatio;
uniform float uThickness;

vec2 getPos(float uVal) {
    float t = uVal * uT;
    float x = uL.x * cos(uOmega.x * t + uPhi.x) +
              uL.y * cos(uOmega.y * t + uPhi.y) +
              uL.z * cos(uOmega.z * t + uPhi.z);

    float y = uL.x * sin(uOmega.x * t + uPhi.x) +
              uL.y * sin(uOmega.y * t + uPhi.y) +
              uL.z * sin(uOmega.z * t + uPhi.z);
    return vec2(x, y);
}

void main() {
    vec2 pos = getPos(u);
    
    // Approximate tangent for thickness offset
    float eps = 0.0001;
    vec2 posNext = getPos(u + eps);
    vec2 tangent = normalize(posNext - pos);
    vec2 normal = vec2(-tangent.y, tangent.x);

    vec2 finalPos = pos + normal * uThickness * side;

    // Apply global rotation
    float cosR = cos(uGlobalRotation);
    float sinR = sin(uGlobalRotation);
    
    float rotX = finalPos.x * cosR - finalPos.y * sinR;
    float rotY = finalPos.x * sinR + finalPos.y * cosR;

    if (uAspectRatio > 1.0) {
        rotX /= uAspectRatio;
    } else {
        rotY *= uAspectRatio;
    }

    gl_Position = vec4(rotX, rotY, 0.0, 1.0);
}
