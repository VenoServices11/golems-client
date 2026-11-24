#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LobbyBlur {
    vec2 Dir;
    float Threshold;
};

in vec2 texCoord;
out vec4 fragColor;


vec3 plain(vec2 uv) {
    vec3 c = texture(InSampler, uv).rgb;
    return c;
}

vec3 bright(vec2 uv) {
    vec3 c = plain(uv);
    float peak = max(c.r, max(c.g, c.b));
    float excess = clamp((peak - Threshold) / (1.0 - Threshold), 0.0, 1.0);
    float chroma = (peak - min(c.r, min(c.g, c.b))) / max(peak, 0.001);
    return c * excess * excess * mix(0.35, 1.0, chroma);
}

void main() {
    vec2 offset = Dir / OutSize;
    vec3 sum;
    if (Threshold <= 0.0) {
        sum = plain(texCoord) * 0.2;
        sum += (plain(texCoord + offset * 1.4) + plain(texCoord - offset * 1.4)) * 0.18;
        sum += (plain(texCoord + offset * 3.3) + plain(texCoord - offset * 3.3)) * 0.14;
        sum += (plain(texCoord + offset * 5.2) + plain(texCoord - offset * 5.2)) * 0.08;
    } else {
        sum = bright(texCoord) * 0.2;
        sum += (bright(texCoord + offset * 1.4) + bright(texCoord - offset * 1.4)) * 0.18;
        sum += (bright(texCoord + offset * 3.3) + bright(texCoord - offset * 3.3)) * 0.14;
        sum += (bright(texCoord + offset * 5.2) + bright(texCoord - offset * 5.2)) * 0.08;
    }
    fragColor = vec4(sum, 1.0);
}
