#version 300 es
precision highp float;

uniform float uTime;

out vec4 fragColor;

// Helper to convert HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // Color cycling based on time
    float hue = fract(uTime * 0.1);
    vec3 color = hsv2rgb(vec3(hue, 0.8, 1.0));
    fragColor = vec4(color, 1.0);
}
