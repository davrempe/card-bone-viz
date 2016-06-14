precision mediump float;

varying vec4 v_Color;
varying float v_Diffuse;


void main() {
    gl_FragColor = v_Diffuse * v_Color;
}
