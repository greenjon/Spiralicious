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
    // Note: uDecay is preserved for historical reasons but not actively used
    // 
    // The uGain value is now mapped from raw 0-1 to a better scaled 0.5-2.0 range
    // with enhanced precision in the useful band (previously 0.5-0.75)
    vec3 feedbackPart = history.rgb * uGain;
    
    // Use max blend for the cleanest feedback effect
    vec3 composite = max(live.rgb, feedbackPart);
    
    // Persistence: Use max for alpha but multiply by gain
    // Clamp to avoid over-persistence with high gain values
    float alpha = max(live.a, history.a * min(uGain, 1.0));
    
    fragColor = vec4(composite, alpha);
}
