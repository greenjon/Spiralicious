#version 300 es
precision highp float;

out vec4 fragColor;

void main() {
    // Basic high precision white output
    // In the future, we can add color based on u or distance from center
    fragColor = vec4(0.0, 0.8, 1.0, 1.0); // A nice cyan for now
}
