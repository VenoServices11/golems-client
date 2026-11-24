const float POLYPLUS_WAVE_WIDTH = 10.0;
const float POLYPLUS_WAVE_DEPTH = 4.0;
const float POLYPLUS_WAVE_CREST = 0.5;

float golemsclient_front(vec3 golemsclient_position) {
    if (golemsclient_LobbyWave == vec4(0.0)) {
        return 1.0;
    }
    float fromAim = max(dot(golemsclient_position.xz, golemsclient_LobbyWave.xy) - golemsclient_LobbyWave.z, 0.0);
    return golemsclient_LobbyWave.w - fromAim;
}

float golemsclient_lift(vec3 golemsclient_position) {
    if (golemsclient_LobbyWave == vec4(0.0)) {
        return 0.0;
    }
    float risen = clamp(golemsclient_front(golemsclient_position) / POLYPLUS_WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * POLYPLUS_WAVE_CREST;
    return (eased - 1.0) * POLYPLUS_WAVE_DEPTH + crest;
}
