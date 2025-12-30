#version 300 es

layout(location = 0) in float u;

void main() {
    // For now, just a placeholder. 
    // We will compute the actual position in the next phase.
    gl_Position = vec4(u * 2.0 - 1.0, 0.0, 0.0, 1.0);
    gl_PointSize = 2.0;
}
