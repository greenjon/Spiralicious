#version 330 core
out vec4 FragColor;

in vec2 TexCoords;

uniform sampler2D u_newFrame;   // Texture with the freshly drawn mandala
uniform sampler2D u_lastFrame;  // Texture with the previous frame's output

uniform float u_feedbackAmount; // How much of the old frame to keep (0-1)
uniform mat3 u_feedbackMatrix;  // For transforming the feedback layer

void main() {
    // Transform the texture coordinates for the feedback layer
    vec2 transformedCoords = (u_feedbackMatrix * vec3(TexCoords, 1.0)).xy;

    vec4 newColor = texture(u_newFrame, TexCoords);
    vec4 lastColor = texture(u_lastFrame, transformedCoords);

    // Mix them. `max` creates a nice additive blend for generative art.
    FragColor = max(newColor, lastColor * u_feedbackAmount);
}
