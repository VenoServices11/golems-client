#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

#moj_import <minecraft:chunksection.glsl>


in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float chunkVisibility;
out float waveFront;

#define AIM_DISTANCE FogEnvironmentalEnd

vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}


const float SECONDS_PER_DAY = 1200.0;
const float WAVE_SPEED = 28.0;
const float WAVE_WIDTH = 10.0;
const float WAVE_DEPTH = 4.0;
const float WAVE_CREST = 0.5;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;

    float seconds = GameTime * SECONDS_PER_DAY;
    float distanceToAim = AIM_DISTANCE;
    vec3 forward = -vec3(ModelViewMat[0][2], ModelViewMat[1][2], ModelViewMat[2][2]);
    vec3 aim = forward * distanceToAim;
    float level = length(forward.xz) + 1e-4;
    vec2 across = forward.xz / level;
    float aimAlong = level * distanceToAim;
    float dip = atan(-forward.y, level) + atan(1.0 / ProjMat[1][1]);
    float start = dip <= 0.0 ? aimAlong : (dip >= 1.5707 ? 0.0 : clamp(-aim.y / tan(dip), 0.0, aimAlong));
    float fromAim = max(dot(pos.xz, across) - start, 0.0);
    float behindFront = seconds * WAVE_SPEED - fromAim;
    float risen = clamp(behindFront / WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * WAVE_CREST;
    pos.y += (eased - 1.0) * WAVE_DEPTH + crest;
    waveFront = behindFront;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
