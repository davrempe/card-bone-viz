precision mediump float;

varying vec4 v_Color;
varying float v_Diffuse;

void main() {
    vec4 tempColor = v_Diffuse * v_Color;
    tempColor.a = 1.0;
    gl_FragColor =  tempColor;
}
