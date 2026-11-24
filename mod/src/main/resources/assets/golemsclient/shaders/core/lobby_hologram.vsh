#version 330


#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;


out vec3 viewPosition;
out vec3 viewNormal;
out vec2 texCoord0;
out vec3 accent;
out vec4 overlayColor;
out vec4 ambientColor;
out float height;
out float progress;
out float seed;
out float prepass;


void main() {
    vec4 eye = ModelViewMat * vec4(Position + Normal * 0.006, 1.0);
    gl_Position = ProjMat * eye;
    viewPosition = eye.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    texCoord0 = UV0;
    accent = vec3((UV2.y >> 10) & 31, (UV2.y >> 5) & 31, UV2.y & 31) / 31.0;
    overlayColor = texelFetch(Sampler1, UV1, 0);
    ambientColor = texelFetch(Sampler2, ivec2(15, 15), 0);
    height = Position.y;
    progress = float(UV2.x & 1023) / 1023.0;
    seed = float((UV2.x >> 10) & 15) / 15.0;
    prepass = float((UV2.x >> 14) & 1);
}
