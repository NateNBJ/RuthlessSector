package ruthless_sector.combat;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import ruthless_sector.CombatPlugin;

public class JammerEffect implements BeamEffectPlugin {
    public static final String ID = "sun_rs_jammer_effect";
    public static float RANGE_MULT = 0.7f;
    public static float ECCM_RANGE_MULT = 0.8f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if(beam.getDamageTarget() == null || !(beam.getDamageTarget() instanceof ShipAPI)) return;

        ShipAPI target = (ShipAPI)beam.getDamageTarget();
        float mult = target.getVariant().hasHullMod("eccm") ? ECCM_RANGE_MULT : RANGE_MULT;

        CombatPlugin.applyJammerPenaltyToShip(target, mult + (1 - mult) * (1 - beam.getWeapon().getChargeLevel()));
    }
}
