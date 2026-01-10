#version 300 es
precision highp float;

uniform float uHueOffset;
uniform float uHueSweep;
uniform float uAlpha;

uniform float uDepth;
uniform float uMinR;
uniform float uMaxR;

in float vPhase;
in vec2 vRawPos;

out vec4 fragColor;

// Branchless HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float hue = fract(uHueOffset + (vPhase * uHueSweep));
    
    // Perceptual Depth Math
    float r = length(vRawPos);
    float rNorm = clamp((r - uMinR) / max(0.001, uMaxR - uMinR), 0.0, 1.0);
    
    // Gamma correction for perceptual curve
    float f = pow(rNorm, 0.7);
    
    // Final Value calculation
    float V = 0.85 * (1.0 - uDepth + uDepth * f);
    
    // Perceptual tuning: Saturation 0.8, Value V
    vec3 rgb = hsv2rgb(vec3(hue, 0.8, V));
    
    // Pre-multiplied alpha for blending consistency
    fragColor = vec4(rgb * uAlpha, uAlpha);
}
