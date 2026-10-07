#version 330

// 1.21.11 PORT of shaders/program/cooptest_chroma.fsh:
//  - post shaders moved to assets/<ns>/shaders/post with the 1.21.2+
//    post_effect JSON format,
//  - GLSL 330 with std140 uniform blocks (1.21.9+),
//  - DiffuseSampler renamed to the pass-declared "In" sampler.
// The chroma math is unchanged.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform ChromaConfig {
    float ChromaOffset;
};

out vec4 fragColor;

void main() {
    vec2 dir  = texCoord - vec2(0.5);
    float dist = length(dir);
    // Edge-based split: stronger at corners, zero at center
    vec2 offset = (dist > 0.001)
        ? normalize(dir) * ChromaOffset * dist * 2.0
        : vec2(0.0);

    float r = texture(InSampler, texCoord + offset).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - offset).b;

    fragColor = vec4(r, g, b, 1.0);
}
