package ruthless_sector;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;
import hyperdrive.campaign.abilities.HyperdriveAbility;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float CORE_RADIUS = 22000;
    static final float MAX_REMNANT_STRENGTH_DISTANCE_FROM_CORE_PERCENTAGE = 0.5f;
    static final int MAX_REMNANT_FLEETS = 6;

    static boolean playerJustRespawned = false, timeHasPassedSinceLastEngagement = true;

    Saved<Float> distanceToNextEncounter = new Saved("distanceToNextEncounter", 100f);
    Saved<LinkedList<CampaignFleetAPI>> remnantFleets = new Saved<>("remnantFleets", new LinkedList<CampaignFleetAPI>());

    CampaignFleetAPI pf;
    Random random = new Random();
    float messageDelay = 0.5f;
    double pfStrength;

    public CampaignScript() { super(true); }

    @Override
    public void advance(float amount) {
        try {
            if(!ModPlugin.readSettingsIfNecessary()) return;

            CombatPlugin.clearDomainDroneEcmBonusFlag();

            pf = Global.getSector().getPlayerFleet();

            if(pf == null) return;

            pfStrength = ModPlugin.tallyShipStrength(pf.getFleetData().getMembersListCopy());

            if(ModPlugin.OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY) {
                final ViewportAPI view = Global.getSector().getViewport();
                final Vector2f target = new Vector2f(view.convertScreenXToWorldX(Mouse.getX()),
                        view.convertScreenYToWorldY(Mouse.getY()));

                for (CampaignFleetAPI flt : Misc.getVisibleFleets(Global.getSector().getPlayerFleet(), true)) {
                    if (Misc.getDistance(target, flt.getLocation()) < flt.getRadius()) {
                        try {
                            flt.inflateIfNeeded();
                        } catch (Exception e) {
                            Global.getLogger(this.getClass()).warn("Failed to inflate fleet");
                            ModPlugin.reportCrash(e, false);
                        }

                        double efStrength = ModPlugin.tallyShipStrength(flt.getFleetData().getMembersListCopy());

                        flt.getMemoryWithoutUpdate().set("$dangerLevelOverride", getDangerStars(pfStrength, efStrength));
                    }
                }
            }

            if(messageDelay != Float.MIN_VALUE && (messageDelay -= amount) <= 0) {
                double penalty = ModPlugin.getReloadPenalty();

                Global.getLogger(CampaignScript.class).info("Reload Penalty: " + penalty);

                if(penalty > 0) {
                    Global.getSector().getCampaignUI().addMessage("Difficulty rating for the next battle will be reduced by "
                            + Misc.getRoundedValue((float) penalty * 100) + "% due to reloading after battle.", Misc.getNegativeHighlightColor());
                }

                messageDelay = Float.MIN_VALUE;
            }

            if(playerJustRespawned) {
                if(ModPlugin.LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH) {
                    MutableCharacterStatsAPI stats =  Global.getSector().getPlayerStats();
                    long xpAtLevelStart = Global.getSettings().getLevelupPlugin().getXPForLevel(stats.getLevel() - 1);
                    long xpLost = stats.getXP() - xpAtLevelStart;
                    stats.addXP(-xpLost, null, false);

                    if(ModPlugin.SHOW_XP_LOSS_NOTIFICATION && xpLost > 0) {
                        MessageIntel intel = new MessageIntel();
                        intel.addLine("Lost %s experience", Misc.getTextColor(), new String[] { "" + xpLost}, Misc.getHighlightColor() );
                        Global.getSector().getCampaignUI().addMessage(intel);
                    }
                }
                playerJustRespawned = false;
            }

            if(Global.getSector().isPaused()) amount = 0;
            else if(Global.getSector().isInFastAdvance()) amount *= 2;

            if(!Global.getSector().isPaused()) {
                timeHasPassedSinceLastEngagement = true;

                float distanceFromCore = pf.getLocation().length() - CORE_RADIUS;

                if (ModPlugin.ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE && pf.isInHyperspace() && distanceFromCore > 0) {
                    distanceToNextEncounter.val -= amount * pf.getCurrBurnLevel();

                    for(CampaignFleetAPI fleet : remnantFleets.val) {
                        if(fleet != null && fleet.getCurrentAssignment() != null
                                && fleet.getCurrentAssignment().getAssignment() == FleetAssignment.HOLD
                                && Misc.getVisibleFleets(fleet, true).size() > 0) {

                            fleet.clearAssignments();
                            fleet.addAssignment(FleetAssignment.RAID_SYSTEM, null, Float.MAX_VALUE);
                        }
                    }
                }

                if (distanceToNextEncounter.val <= 0) {
                    float d = ModPlugin.AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS;
                    distanceToNextEncounter.val = d * 0.5f + random.nextFloat() * d;
                    Vector2f loc = new Vector2f(pf.getLocation());
                    Vector2f.add(loc, (Vector2f) pf.getVelocity().normalise().scale(2800), loc);
                    spawnRemnantFleets(distanceFromCore, loc, 800f, false);
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    void spawnRemnantFleets(float distanceFromCore, Vector2f loc, float variationRadius, boolean isAlerted) {
        float maxCombatPoints = ModPlugin.MAX_HYPERSPACE_REMNANT_STRENGTH,
                sectorInnerRadius = Global.getSettings().getFloat("sectorHeight") * 0.5f,
                powerScale = Math.min(1, distanceFromCore / (sectorInnerRadius - CORE_RADIUS) / MAX_REMNANT_STRENGTH_DISTANCE_FROM_CORE_PERCENTAGE);

        log("Spawning remnant fleets with " + (int)(powerScale * 100) + "% of max power");

        purgeOldestRemnantFleetsIfNeeded();

        int fleetsSpawned = 0;

        do {
            CampaignFleetAPI fleet = null;

            while (fleet == null) {
                int combatPoints = 1 + random.nextInt((int) Math.max(1, maxCombatPoints * powerScale));

                String type = FleetTypes.PATROL_SMALL;
                if (combatPoints > 8) type = FleetTypes.PATROL_MEDIUM;
                if (combatPoints > 16) type = FleetTypes.PATROL_LARGE;

                combatPoints *= 8f; // 8 is fp cost of remnant frigate

                FleetParamsV3 params = new FleetParamsV3(
                        pf.getLocation(),
                        Factions.REMNANTS,
                        1f,
                        type,
                        combatPoints, // combatPts
                        0f, // freighterPts
                        0f, // tankerPts
                        0f, // transportPts
                        0f, // linerPts
                        0f, // utilityPts
                        0f // qualityMod
                );
                params.withOfficers = true;
                params.random = random;

                try {
                    fleet = FleetFactoryV3.createFleet(params);
                } catch (Exception e) {
                    Global.getLogger(ModPlugin.class).warn("Failed to generate fleet: " + params.toString());
                    ModPlugin.reportCrash(e, false);
                }
            }

            remnantFleets.val.add(fleet);
            pf.getContainingLocation().addEntity(fleet);
            RemnantSeededFleetManager.initRemnantFleetProperties(random, fleet, false);
            Vector2f.add(loc, Misc.getPointAtRadius(new Vector2f(), variationRadius, random), loc);
            fleet.setLocation(loc.x, loc.y);
//            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, false);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
            fleet.setFacing(random.nextFloat() * 360f);
//        fleet.getStats().getSensorStrengthMod().modifyMult("sun_rs_remnant_sensor_bonus", 2);

            if (isAlerted) fleet.addAssignment(FleetAssignment.RAID_SYSTEM, null, Float.MAX_VALUE, "hunting");
            else fleet.addAssignment(FleetAssignment.HOLD, null, Float.MAX_VALUE, "laying in wait");

            fleet.setTransponderOn(true);

            fleetsSpawned++;
        } while (random.nextFloat() < ModPlugin.CHANCE_OF_ADDITIONAL_HYPERSPACE_REMNANT_FLEETS
                && fleetsSpawned < ModPlugin.MAX_HYPERSPACE_REMNANT_FLEETS_TO_SPAWN_AT_ONCE
                && remnantFleets.val.size() < MAX_REMNANT_FLEETS);
    }

    void purgeOldestRemnantFleetsIfNeeded() {
        for (int i = 0; remnantFleets.val.size() > MAX_REMNANT_FLEETS && remnantFleets.val.size() > i; ) {
            CampaignFleetAPI rf = remnantFleets.val.get(i);

            if(rf.isVisibleToPlayerFleet()) {
                i++;
            } else {
                rf.despawn();
                remnantFleets.val.remove(i);
            }
        }
    }

    void despawnOrResetAssignment(CampaignFleetAPI fleet) {
        //   This function needs to stay this way to maintain backwards compatibility with saves that kept references
        // to a CampaignScript instance via the script

        pf = Global.getSector().getPlayerFleet();
        float dist = pf == null
                ? Float.MAX_VALUE
                : Misc.getDistanceLY(pf.getLocationInHyperspace(), fleet.getLocationInHyperspace());

        if(dist > 6) {
            fleet.despawn(FleetDespawnReason.PLAYER_FAR_AWAY, null);
            log("Despawned " + fleet.getName());
        } else {
            final CampaignFleetAPI ffleet = fleet;
            fleet.addAssignment(FleetAssignment.HOLD, null, Float.MAX_VALUE, "waiting", new Script() {
                @Override
                public void run() { }
            });
        }
    }

    public static void setPlayerJustRespawned() { playerJustRespawned = true; }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            EngagementResultForFleetAPI pf = result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();

            Map<FleetMemberAPI, Float> playerDeployedFP = new HashMap();

            for(FleetMemberAPI fm : pf.getDeployed()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getRetreated()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getDisabled()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getDestroyed()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
            
            float deployedStrength = 0;

            for(Float strength : playerDeployedFP.values()) deployedStrength += strength;

            ModPlugin.updatePlayerStrength(deployedStrength);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return true; }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if(battle.isPlayerInvolved()) {
                CombatPlugin.clearDomainDroneEcmBonusFlag();
                ModPlugin.battlesResolvedSinceLastSave++;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // Commission bounties aren't actually payed in Starsector 0.9.1-RC10 in spite of the notification. This fixes that.
        // TODO - Remove this once the bug is fixed

        FactionCommissionIntel intel = Misc.getCommissionIntel();

        if (intel == null || intel.isEnded() || intel.isEnding()) return;

        if (!battle.isPlayerInvolved()) return;

        int payment = 0;
        for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
            if (!intel.getFactionForUIColors().isHostileTo(otherFleet.getFaction())) continue;

            float bounty = 0;
            for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
                float mult = Misc.getSizeNum(loss.getHullSpec().getHullSize());
                bounty += mult * Global.getSettings().getFloat("factionCommissionBounty");
            }

            payment += (int) (bounty * battle.getPlayerInvolvementFraction());
        }

        if (payment > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        try {
            pf = Global.getSector().getPlayerFleet();

            for (int i = 0; i < remnantFleets.val.size() && i >= 0; i++) {
                CampaignFleetAPI rf = remnantFleets.val.get(i);
                float dist = pf == null
                        ? Float.MAX_VALUE
                        : Misc.getDistanceLY(pf.getLocationInHyperspace(), rf.getLocationInHyperspace());

                if (dist > 6) {
                    log("Despawned " + rf.getName());
                    rf.despawn(FleetDespawnReason.PLAYER_FAR_AWAY, null);
                    remnantFleets.val.remove(i);
                    i--;
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {
        super.reportPlayerActivatedAbility(ability, param);


//        float dfc = pf.getLocation().length() - CORE_RADIUS;
//        float sectorInnerRadius = Global.getSettings().getFloat("sectorHeight") * 0.5f,
//                powerScale = Math.min(1, dfc / (sectorInnerRadius - CORE_RADIUS) / MAX_REMNANT_STRENGTH_DISTANCE_FROM_CORE_PERCENTAGE);
//
//        Global.getSector().getCampaignUI().addMessage("Power Scale: " + (int)(powerScale * 100) + "%");

        if(ModPlugin.ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE
                && (pf != null && pf.isInHyperspace())
                && Global.getSettings().getModManager().isModEnabled("sun_hyperdrive")
                && ability.getId().equals("sun_hd_hyperdrive")
                && Math.random() < (600f / ModPlugin.AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS)) {

            HyperdriveAbility wd = (HyperdriveAbility)ability;
            Vector2f at = new Vector2f(wd.getDestinationToken().getLocation());
            Vector2f lead = Misc.getUnitVectorAtDegreeAngle(pf.getFacing());
            lead.scale(2500);
            Vector2f.add(at, lead, at);
            float distanceFromCore = at.length() - CORE_RADIUS;

            if(distanceFromCore > 0) {
                at = Misc.getPointAtRadius(at, 3500);

                spawnRemnantFleets(distanceFromCore, at, 250, true);
            }
        }
    }

    static int getDangerStars(double playerStrength, double enemyStrength) {
        int danger = 10;

        if(playerStrength <= 0) return danger;

        double ratio = enemyStrength / playerStrength;

        if(ratio < 0.50) danger = 1;
        else if(ratio < 0.75) danger = 2;
        else if(ratio < 1.00) danger = 3;
        else if(ratio < 1.25) danger = 4;
        else if(ratio < 1.50) danger = 5;
        else if(ratio < 2.00) danger = 6;
        else if(ratio < 3.00) danger = 7;
        else if(ratio < 4.00) danger = 8;
        else if(ratio < 5.00) danger = 9;

        return danger;
    }
}