#version 330

// 1.21.11 PORT: core shader JSONs were removed in 1.21.6; this pipeline is
// registered in code (CoopImpactRenderType) from RenderPipelines
// .ENTITY_SNIPPET, and the GLSL uses the 1.21.9+ UBO includes. Output is
// unchanged: entity silhouettes with texture-alpha cutout.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
}
