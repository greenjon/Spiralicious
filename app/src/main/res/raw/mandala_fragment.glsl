#version 300 es
precision highp float;

uniform vec4 uColor; // (Hue, Sat, Val, Alpha)

out vec4 fragColor;

// Helper to convert HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // uColor.x = Hue (0-1), uColor.y = Sat (0-1), uColor.z = Val (fixed 1.0), uColor.w = Alpha
    vec3 rgb = hsv2rgb(vec3(uColor.x, uColor.y, 1.0));
    
    // PRE-MULTIPLIED ALPHA:
    // This is crucial for consistent gain application across all blending modes (ADD, SCREEN, etc.)
    fragColor = vec4(rgb * uColor.w, uColor.w);
}
