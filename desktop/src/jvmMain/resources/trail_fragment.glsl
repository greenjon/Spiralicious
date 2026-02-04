#version 300 es
precision highp float;

uniform sampler2D uTexture;
uniform float uAlpha;
in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(uTexture, vTexCoord);
    // As suggested: Multiply only the alpha channel for the blend function to use
    fragColor = vec4(color.rgb, color.a * uAlpha);
}
