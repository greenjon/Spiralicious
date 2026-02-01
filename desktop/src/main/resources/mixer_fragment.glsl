#version 300 es
precision mediump float;

uniform sampler2D uTex1;
uniform sampler2D uTex2;
uniform int uMode; // 0: ADD, 1: SCREEN, 2: MULT, 3: MAX, 4: XFADE
uniform float uBalance;
uniform float uAlpha;

in vec2 vTexCoord;
out vec4 outColor;

void main() {
    vec4 c1 = texture(uTex1, vTexCoord);
    vec4 c2 = texture(uTex2, vTexCoord);

    vec4 result;
    if (uMode == 0) { // ADD
        result = c1 * (1.0 - uBalance * 2.0) + c2 * (uBalance * 2.0); // Simple crossfade for ADD logic in shader? No, let's follow the GL blend modes logic
        // Actually, the request said: "Draw a quad that samples from Slot FBOs... using a new Mixer shader"
        // Let's implement the specific modes:
        result = c1 + c2; // Placeholder
    }
    
    // Improved Mixer Logic to match GL blend modes
    float bal1 = clamp((1.0 - uBalance) * 2.0, 0.0, 1.0);
    float bal2 = clamp(uBalance * 2.0, 0.0, 1.0);
    
    vec4 s1 = c1 * bal1;
    vec4 s2 = c2 * bal2;

    if (uMode == 0) { // ADD
        result = s1 + s2;
    } else if (uMode == 1) { // SCREEN
        result = 1.0 - (1.0 - s1) * (1.0 - s2);
    } else if (uMode == 2) { // MULT
        result = s1 * s2;
    } else if (uMode == 3) { // MAX
        result = max(s1, s2);
    } else { // XFADE
        result = mix(s1, s2, uBalance); // Simplified XFADE
    }

    outColor = result * uAlpha;
}
