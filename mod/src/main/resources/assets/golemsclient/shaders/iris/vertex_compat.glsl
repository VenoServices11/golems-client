vec4 golemsclient_vertex() {
    vec4 golemsclient_position = gl_Vertex;
    golemsclient_position.y += golemsclient_lift(golemsclient_position.xyz);
    return golemsclient_position;
}
