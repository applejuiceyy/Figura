package org.moon.figura.model.rendering.texture;

import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

public class FiguraTextureSet {

    public final String name;
    public final FiguraTexture mainTex, emissiveTex;
    private final Map<String, RenderType> renderTypes = new HashMap<>();

    public FiguraTextureSet(String name, byte[] mainData, byte[] emissiveData) {
        this.name = name;
        mainTex = mainData == null ? null : new FiguraTexture(mainData);
        emissiveTex = emissiveData == null ? null : new FiguraTexture(emissiveData);
    }

    public void uploadIfNeeded() {
        if (mainTex != null)
            mainTex.registerAndUpload();
        if (emissiveTex != null)
            emissiveTex.registerAndUpload();
    }

    public RenderType getRenderType(String name) {
        if (name == null)
            return null;
        if (!renderTypes.containsKey(name)) {
            switch (name) {
                case "CUTOUT_NO_CULL" -> renderTypes.put("CUTOUT_NO_CULL", mainTex == null ? null : RenderType.entityCutoutNoCull(mainTex.textureID));
                case "CUTOUT" -> renderTypes.put("CUTOUT", mainTex == null ? null : RenderType.entityCutout(mainTex.textureID));

                case "EMISSIVE" -> renderTypes.put("EMISSIVE", emissiveTex == null ? null : RenderType.eyes(emissiveTex.textureID));
                case "EMISSIVE_SOLID" -> renderTypes.put("EMISSIVE_SOLID", emissiveTex == null ? null : RenderType.beaconBeam(emissiveTex.textureID, false));

                case "END_PORTAL" -> renderTypes.put("END_PORTAL", RenderType.endPortal());
                case "GLINT" -> renderTypes.put("GLINT", RenderType.glintDirect());
                case "GLINT2" -> renderTypes.put("GLINT2", RenderType.entityGlintDirect());

                default -> throw new IllegalArgumentException("Illegal render type name \"" + name + "\".");
            }
        }
        return renderTypes.get(name);
    }

}
