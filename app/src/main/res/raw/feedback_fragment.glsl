#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureLive;
uniform sampler2D uTextureHistory;

uniform float uDecay;
uniform float uGain;
uniform float uZoom;   
uniform float uRotate; 
uniform float uHueShift;
uniform float uBlur;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

void main() {
    // 1. Transform History Coordinates
    vec2 uv = vTexCoord - 0.5;
    uv *= (1.0 - uZoom);
    float s = sin(uRotate);
    float c = cos(uRotate);
    uv = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);
    vec2 historyCoord = uv + 0.5;
    
    vec4 history;
    if (uBlur > 0.001) {
        float b = uBlur * 0.004;
        history = texture(uTextureHistory, historyCoord) * 0.4;
        history += texture(uTextureHistory, historyCoord + vec2(b, 0.0)) * 0.15;
        history += texture(uTextureHistory, historyCoord + vec2(-b, 0.0)) * 0.15;
        history += texture(uTextureHistory, historyCoord + vec2(0.0, b)) * 0.15;
        history += texture(uTextureHistory, historyCoord + vec2(0.0, -b)) * 0.15;
    } else {
        history = texture(uTextureHistory, historyCoord);
    }
    
    // 2. Color Shift
    if (uHueShift != 0.0 && history.a > 0.01) {
        vec3 hsv = rgb2hsv(history.rgb);
        hsv.x = fract(hsv.x + uHueShift);
        history.rgb = hsv2rgb(hsv);
    }
    
    // 3. Sample Live Signal
    vec4 live = texture(uTextureLive, vTexCoord);
    
    // 4. Max Lighten Blend
    // This preserves hues and prevents additive white-out.
    // We apply uGain to allow compensating for Blur/Zoom energy loss.
    vec3 feedbackPart = history.rgb * uGain; // Removed uDecay here to preserve feedback energy
    
    // Blend between live and feedback using gain as the factor
    // This ensures we always see something, even with low gain
    vec3 composite = mix(live.rgb, max(live.rgb, feedbackPart), clamp(uGain, 0.0, 1.0));
    
    // Persistence: Use max for alpha but apply gain to preserve trails
    float alpha = max(live.a, history.a * clamp(uGain, 0.1, 1.0));
    
    fragColor = vec4(composite, alpha);
}
