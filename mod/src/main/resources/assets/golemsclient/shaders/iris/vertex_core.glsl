vec3 golemsclient_vaPosition() {
    vec3 golemsclient_position = vaPosition + chunkOffset;
    golemsclient_position.y += golemsclient_lift(golemsclient_position);
    return golemsclient_position - chunkOffset;
}
