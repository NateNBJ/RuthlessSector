package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfare;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfareScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import ruthless_sector.combat.JammerEffect;

import java.util.*;

public class CombatPlugin extends StrengthTrackingCombatPlugin {
    public static final String ECM_ID = "sun_rs_jammer_effect";

    static float timeUntilReaplyEcmBonus = Float.MAX_VALUE;

    public static void setDomainDroneEcmBonusFlag() { timeUntilReaplyEcmBonus = 1; }
    public static void clearDomainDroneEcmBonusFlag() { timeUntilReaplyEcmBonus = Float.MAX_VALUE; }

    static class JamTracker {
        float current = 0, goal;

        JamTracker(float startGoal) { this.goal = startGoal; }
        void adjust(float amount) {
            current = Math.max(Math.min(current + Math.signum(goal - current) * amount, goal), goal);
        }
    }

    static Map<ShipAPI, JamTracker> jammerPenalties = new HashMap();
    static Set<ShipAPI> formerJammerVictims = new HashSet();

    public static void applyJammerPenaltyToShip(ShipAPI ship, float mult) {
        if(ship == null) return;
        else if(!jammerPenalties.containsKey(ship)) jammerPenalties.put(ship, new JamTracker(mult));
        else jammerPenalties.get(ship).goal *= mult;
    }


    float totalStandardRating = 0;

    void doJammerStuff(float amount) {
        for(Map.Entry<ShipAPI, JamTracker> e : jammerPenalties.entrySet()) {
            MutableShipStatsAPI stats = e.getKey().getMutableStats();
            JamTracker tracker = e.getValue();
            tracker.adjust(amount);
            float mult = tracker.current;

            if(mult >= 1) {
                stats.getFighterWingRange().unmodify(JammerEffect.ID);
                stats.getMissileWeaponRangeBonus().unmodify(JammerEffect.ID);
                stats.getEnergyWeaponRangeBonus().unmodify(JammerEffect.ID);
                stats.getBallisticWeaponRangeBonus().unmodify(JammerEffect.ID);

                formerJammerVictims.add(e.getKey());
            } else {
                stats.getFighterWingRange().modifyMult(JammerEffect.ID, mult);
                stats.getMissileWeaponRangeBonus().modifyMult(JammerEffect.ID, mult);
                stats.getEnergyWeaponRangeBonus().modifyMult(JammerEffect.ID, mult);
                stats.getBallisticWeaponRangeBonus().modifyMult(JammerEffect.ID, mult);
            }

            tracker.goal = 1.01f;
        }

        for(ShipAPI ship : formerJammerVictims) jammerPenalties.remove(ship);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        super.advance(amount, events);

        try {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine == null || engine.getShips() == null) return;

            if(timeUntilReaplyEcmBonus != Float.MAX_VALUE && !engine.isPaused()) timeUntilReaplyEcmBonus -= amount;

            if(timeUntilReaplyEcmBonus <= 0) {

                timeUntilReaplyEcmBonus = 5;

                Global.getLogger(this.getClass()).info("reaply");

                if(totalStandardRating == 0) {
                    for (ShipAPI ship : engine.getShips()) {
                        if (ship.getOriginalOwner() == 1) {
                            totalStandardRating += ElectronicWarfare.getBase(ship.getHullSize());
                        }
                    }
                }

                for(ShipAPI ship : engine.getShips()) {
                    if(ship.getHullSpec().hasTag("derelict") && ship.getOriginalOwner() == 1) {
                        MutableShipStatsAPI stats = ship.getMutableStats();
                        float bonus = ElectronicWarfare.getBase(ship.getHullSize());
                        bonus += ModPlugin.FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES * (bonus / totalStandardRating);

                        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(ECM_ID, bonus);
                        stats.getBallisticWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        stats.getEnergyWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        stats.getMissileWeaponRangeBonus().modifyMult(ECM_ID, ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                    }
                }

                float ew_cap = ModPlugin.MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES - ElectronicWarfareScript.BASE_MAXIMUM;
                DynamicStatsAPI stats = engine.getFleetManager(FleetSide.ENEMY).getFleetCommander().getStats().getDynamic();
                stats.getMod(Stats.ELECTRONIC_WARFARE_MAX).modifyFlat(ECM_ID, ew_cap);

            }

        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
