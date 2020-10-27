package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfare;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfareScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.DynamicStatsAPI;

import java.util.*;

public class CombatPlugin extends StrengthTrackingCombatPlugin {
    public static final String ECM_ID = "sun_rs_jammer_effect";

    static float timeUntilReaplyEcmBonus = Float.MAX_VALUE;

    public static void setDomainDroneEcmBonusFlag() { timeUntilReaplyEcmBonus = 1; }
    public static void clearDomainDroneEcmBonusFlag() { timeUntilReaplyEcmBonus = Float.MAX_VALUE; }

    float totalStandardRating = 0;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        super.advance(amount, events);

        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || engine.getShips() == null || !engine.isInCampaign()) return;

            if(timeUntilReaplyEcmBonus != Float.MAX_VALUE && !engine.isPaused()) timeUntilReaplyEcmBonus -= amount;

            if(timeUntilReaplyEcmBonus <= 0) {

                timeUntilReaplyEcmBonus = 5;

                //Global.getLogger(this.getClass()).info("reaply");

                if(totalStandardRating == 0) {
                    for (ShipAPI ship : engine.getShips()) {
                        if (ship.getOriginalOwner() == 1) {
                            totalStandardRating += ElectronicWarfare.getBase(ship.getHullSize());
                        }
                    }


                    //Global.getLogger(this.getClass()).info("totalStandardRating: " + totalStandardRating);
                }

                for(ShipAPI ship : engine.getShips()) {
                    if(ship.getOriginalOwner() == 1) {
                    //    if(ship.getHullSpec().hasTag("derelict") && ship.getOriginalOwner() == 1) {
                        MutableShipStatsAPI stats = ship.getMutableStats();
                        float bonus = ElectronicWarfare.getBase(ship.getHullSize());
                        bonus += ModPlugin.FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES * (bonus / totalStandardRating);

                        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(ECM_ID, bonus);
                        stats.getBallisticWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        stats.getEnergyWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        stats.getMissileWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);


                        //Global.getLogger(this.getClass()).info("tags: " + ship.getHullSpec().getTags());
                    }
                }

                CombatFleetManagerAPI cfm = engine.getFleetManager(FleetSide.ENEMY);

                if(cfm.getFleetCommander() != null) {
                    float ew_cap = ModPlugin.MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES - ElectronicWarfareScript.BASE_MAXIMUM;
                    DynamicStatsAPI stats = cfm.getFleetCommander().getStats().getDynamic();
                    stats.getMod(Stats.ELECTRONIC_WARFARE_MAX).modifyFlat(ECM_ID, ew_cap);
                }

            }

        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
