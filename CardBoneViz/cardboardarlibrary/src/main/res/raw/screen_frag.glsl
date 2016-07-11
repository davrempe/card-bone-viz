uniform sampler2D u_cameraTexture;    // The input camera feed texture.
uniform sampler2D u_objectsTexture;    // The input objects scene texture.

precision mediump float;

varying vec2 v_TexCoordinate;

void main() {
    gl_FragColor = texture2D(c_Texture, v_TexCoordinate);
}
