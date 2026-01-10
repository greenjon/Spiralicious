#version 300 es
precision highp float;

uniform float uHueOffset;
uniform float uHueSweep;
uniform float uAlpha;

in float vPhase;

out vec4 fragColor;

// Branchless HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float hue = fract(uHueOffset + (vPhase * uHueSweep));
    
    // Perceptual tuning: Saturation 0.8, Value 1.0
    vec3 rgb = hsv2rgb(vec3(hue, 0.8, 1.0));
    
    // Pre-multiplied alpha for blending consistency
    fragColor = vec4(rgb * uAlpha, uAlpha);
}
