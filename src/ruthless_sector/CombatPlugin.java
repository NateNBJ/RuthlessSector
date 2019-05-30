package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean initialized = true;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || engine.getShips() == null) return;

            if(initialized && engine.getTotalElapsedTime(false) > 1) {
                Global.getLogger(this.getClass()).info("first frame");
                for(ShipAPI ship : engine.getShips()) {
                    if(ship.getHullSpec().getManufacturer().equals("Explorarium")) {
                        ship.getMutableStats().getEnergyWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getBallisticWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getMissileWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                    }
                }
                initialized = false;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) { }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

    @Override
    public void renderInUICoords(ViewportAPI viewport) { }

    @Override
    public void init(CombatEngineAPI engine) { }
}
