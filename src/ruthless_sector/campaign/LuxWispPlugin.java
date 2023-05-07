package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.FlickerUtilV2;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class LuxWispPlugin extends BaseCustomEntityPlugin {
    public static Color GLOW_COLOR = new Color(255,165,100,255);
    public static Color LIGHT_COLOR = new Color(255,165,100,255);
    public static float GLOW_FREQUENCY = 0.2f; // on/off cycles per second


    transient private SpriteAPI glow;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        //entity.setDetectionRangeDetailsOverrideMult(0.75f);
        readResolve();
    }

    Object readResolve() {
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    protected float phase = 0f;
    protected FlickerUtilV2 flicker = new FlickerUtilV2();

    public void advance(float amount) {
        phase += amount * GLOW_FREQUENCY;
        while (phase > 1) phase --;

        flicker.advance(amount * 1f);
    }

    public float getFlickerBasedMult() {
        return 0.5f + flicker.getBrightness() * 0.5f;
//
//        float shortage = entity.getMemoryWithoutUpdate().getFloat(VOLATILES_SHORTAGE_KEY);
//        shortage *= 0.33f;
//        if (shortage <= 0f) return 1f;
//
//        //float f = (1f - shortage) + (shortage * flicker.getBrightness());
//        float f = 1f - shortage * flicker.getBrightness();
//        return f;
    }

    public float getGlowAlpha() {
        float glowAlpha = 0f;
        if (phase < 0.5f) glowAlpha = phase * 2f;
        if (phase >= 0.5f) glowAlpha = (1f - (phase - 0.5f) * 2f);
        glowAlpha = 0.75f + glowAlpha * 0.25f;
        glowAlpha *= getFlickerBasedMult();
        if (glowAlpha < 0) glowAlpha = 0;
        if (glowAlpha > 1) glowAlpha = 1;
        return glowAlpha;
    }

    public float getRenderRange() {
        return entity.getRadius() + 1200f;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        Vector2f loc = entity.getLocation();

        float glowAlpha = getGlowAlpha();

        glow.setColor(GLOW_COLOR);

        float w = 200f;
        float h = 200f;

        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.setAdditiveBlend();

        glow.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 5; i++) {
            w *= 0.3f;
            h *= 0.3f;
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * 0.67f);
            glow.renderAtCenter(loc.x, loc.y);
        }

    }
}
