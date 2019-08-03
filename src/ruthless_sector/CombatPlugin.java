package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class CombatPlugin extends StrengthTrackingCombatPlugin {
    boolean domainDronesNeedBonus = true;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        super.advance(amount, events);

        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || engine.getShips() == null) return;

            if(domainDronesNeedBonus && engine.getTotalElapsedTime(false) > 1) {
                for(ShipAPI ship : engine.getShips()) {
                    //Global.getLogger(this.getClass()).info(ship.getHullStyleId() + " " + ship.getMutableStats().getCRLossPerSecondPercent().computeEffective(100));

                    if(ship.getHullSpec().getManufacturer().equals("Explorarium")) {
                        ship.getMutableStats().getEnergyWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getBallisticWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getMissileWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                    }
                }
                domainDronesNeedBonus = false;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
