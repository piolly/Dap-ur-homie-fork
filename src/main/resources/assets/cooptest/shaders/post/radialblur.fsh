#version 330

// 1.21.11 PORT of shaders/program/cooptest_radialblur.fsh (same math; see
// chroma.fsh header for the format changes).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform RadialBlurConfig {
    float BlurStrength;
    float Samples;
};

out vec4 fragColor;

void main() {
    vec2 dir    = texCoord - vec2(0.5);
    vec2 stepv  = dir * (BlurStrength / Samples);
    vec4 color  = vec4(0.0);
    float total = 0.0;

    for (float i = 0.0; i < Samples; i++) {
        float weight = (Samples - i) / Samples; // closer samples weigh more
        color  += texture(InSampler, texCoord - stepv * i) * weight;
        total  += weight;
    }

    fragColor = color / total;
}
