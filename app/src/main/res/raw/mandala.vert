#version 300 es

layout(location = 0) in float u;

uniform vec3 uOmega;
uniform vec3 uL;
uniform vec3 uPhi;
uniform float uT;
uniform float uGlobalRotation;
uniform float uAspectRatio;

void main() {
    float t = u * uT;

    float x = uL.x * cos(uOmega.x * t + uPhi.x) +
              uL.y * cos(uOmega.y * t + uPhi.y) +
              uL.z * cos(uOmega.z * t + uPhi.z);

    float y = uL.x * sin(uOmega.x * t + uPhi.x) +
              uL.y * sin(uOmega.y * t + uPhi.y) +
              uL.z * sin(uOmega.z * t + uPhi.z);

    // Apply global rotation
    float cosR = cos(uGlobalRotation);
    float sinR = sin(uGlobalRotation);
    
    float rotX = x * cosR - y * sinR;
    float rotY = x * sinR + y * cosR;

    // Adjust for aspect ratio (assuming landscape or portrait)
    if (uAspectRatio > 1.0) {
        rotX /= uAspectRatio;
    } else {
        rotY *= uAspectRatio;
    }

    gl_Position = vec4(rotX, rotY, 0.0, 1.0);
    gl_PointSize = 3.0;
}
