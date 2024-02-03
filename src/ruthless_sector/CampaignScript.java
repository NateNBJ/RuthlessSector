package ruthless_sector;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import hyperdrive.campaign.abilities.HyperdriveAbility;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import static ruthless_sector.ModPlugin.MIN_DIFFICULTY_TO_EARN_XP;
import static ruthless_sector.ModPlugin.getAllowedFactions;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float CORE_RADIUS = 25000;
    static final float MAX_REMNANT_STRENGTH_DISTANCE_FROM_CORE_PERCENTAGE = 0.5f;
    static final int MAX_REMNANT_FLEETS = 6;
    static final int MAX_STARS_TO_SHOW = 10;

    static boolean playerJustRespawned = false, timeHasPassedSinceLastEngagement = true;

    Saved<Long> timestampOfNextJealousy = new Saved<>("timestampOfNextJealousy", Long.MAX_VALUE);
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
            if(!ModPlugin.readSettingsIfNecessary(false)) return;

            ModPlugin.applyStartingConditionsIfNeeded();

            if(!Global.getSector().isPaused()) CombatPlugin.clearDomainDroneEcmBonusFlag();

            pf = Global.getSector().getPlayerFleet();

            if(pf == null) return;

            pfStrength = ModPlugin.tallyShipStrength(pf.getFleetData().getMembersListCopy(), true);

            if(ModPlugin.OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY) {
                final ViewportAPI view = Global.getSector().getViewport();
                final Vector2f target = new Vector2f(view.convertScreenXToWorldX(Mouse.getX()),
                        view.convertScreenYToWorldY(Mouse.getY()));

                for (CampaignFleetAPI flt : Misc.getVisibleFleets(Global.getSector().getPlayerFleet(), true)) {
                    if (Misc.getDistance(target, flt.getLocation()) < flt.getRadius()) {
                        try {
                            flt.inflateIfNeeded();

                            double efStrength = ModPlugin.tallyShipStrength(flt.getFleetData().getMembersListCopy(), false);

                            flt.getMemoryWithoutUpdate().set("$dangerLevelOverride",
                                    Math.ceil(getDanger(pfStrength, efStrength)));
                        } catch (Exception e) {
                            Global.getLogger(this.getClass()).warn("Failed to inflate fleet");
                            ModPlugin.reportCrash(e, false);
                        }
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
            else if(Global.getSector().isInFastAdvance()) amount *= Global.getSettings().getFloat("campaignSpeedupMult");

            if(!Global.getSector().isPaused()) {
                timeHasPassedSinceLastEngagement = true;

                if(ModPlugin.LOSE_REPUTATION_FOR_BEING_FRIENDLY_WITH_ENEMIES) {
                    long ts = Global.getSector().getClock().getTimestamp();

                    if(ts >= timestampOfNextJealousy.val) {
                        loseRepWithOneRandomFactionDueToJealousy();

                        timestampOfNextJealousy.val = (long)(ts + 30 * ModPlugin.TIMESTAMP_TICKS_PER_DAY * (Math.random() + 0.5f));
                    } else if(timestampOfNextJealousy.val.equals(Long.MAX_VALUE)) {
                        timestampOfNextJealousy.val = (long)(ts + 60 * ModPlugin.TIMESTAMP_TICKS_PER_DAY);
                    }
                }

                float distanceFromCore = pf.getLocation().length() - CORE_RADIUS;

                if (ModPlugin.ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE
                        && pf.isInHyperspace()
                        && distanceFromCore > 0
                        && !Misc.isInAbyss(pf)) {

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

                if (distanceToNextEncounter.val <= 0 && !Misc.isInAbyss(pf)) {
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
    void loseRepWithOneRandomFactionDueToJealousy() {
        WeightedRandomPicker<Pair<FactionAPI, FactionAPI>> picker = new WeightedRandomPicker<>();

        for (FactionAPI hater : getAllowedFactions()) {
            FactionAPI cf = Misc.getCommissionFaction();
            if (!hater.isShowInIntelTab() || (cf != null && hater.getId().equals(cf.getId()))) continue;

            for (FactionAPI hatersEnemy : getAllowedFactions()) {
                if (!hatersEnemy.isShowInIntelTab() || hater.getId().equals(hatersEnemy.getId())) continue;

                float hate = calculateHate(hater, hatersEnemy);

                if (hate >= 0.25f) picker.add(new Pair(hater, hatersEnemy), (float) Math.pow(hate, 2));
            }
        }

         if (!picker.isEmpty()) {
            Pair<FactionAPI, FactionAPI> winningPair = picker.pick();
            FactionAPI hater = winningPair.one;
            FactionAPI hatersEnemy = winningPair.two;
            float maxHate = 0.01f * ModPlugin.MAX_REP_LOSS;
            float hate = Math.min(maxHate, calculateHate(hater, hatersEnemy) * maxHate);

            hater.adjustRelationship("player", -hate);
            CoreReputationPlugin.addAdjustmentMessage(-hate, hater, null, null, null, null, null, true, 0f,
                    "Change caused by " + hatersEnemy.getRelToPlayer().getLevel().getDisplayName().toLowerCase()
                            + " standing with their enemy, " + hatersEnemy.getDisplayNameWithArticle());
            Global.getSoundPlayer().playUISound("ui_rep_drop", 0.85f, 0.5f);
        }
    }

    public static void setPlayerJustRespawned() { playerJustRespawned = true; }
    public static float getDanger(double playerStrength, double enemyStrength) {
        if(playerStrength < 0.01) return MAX_STARS_TO_SHOW;

        float danger = 0, difficulty = (float) (enemyStrength / playerStrength);

        float MAX_MULT_DIFF = 2.5f;

        if(difficulty <= MAX_MULT_DIFF) {
            return  1 + (difficulty - MIN_DIFFICULTY_TO_EARN_XP) / (MAX_MULT_DIFF - MIN_DIFFICULTY_TO_EARN_XP) * 4f;
        } else {
            return (float) Math.min(MAX_STARS_TO_SHOW, 5 + Math.sqrt (difficulty - MAX_MULT_DIFF));
        }

        ///float xpMult = (float)ModPlugin.getXpMultiplierForDifficulty(enemyStrength / playerStrength);
//        float xpMult = (float) Math.max(0, (enemyStrength / playerStrength - MIN_DIFFICULTY_TO_EARN_XP) * XP_MULTIPLIER_AFTER_REDUCTION);
//
//        return Math.min(MAX_STARS_TO_SHOW, 1 + xpMult / MAX_XP_MULTIPLIER * 4);
    }
    public static float calculateHate(FactionAPI ofFaction, FactionAPI enemyFaction) {
        final float
            relToPlayer = ofFaction.getRelToPlayer().getRel(), // 1
            enemyRelToPlayer = enemyFaction.getRelToPlayer().getRel(), // -0.5
            relToEnemy = ofFaction.getRelationship(enemyFaction.getId()); // -0.5

        if(!ModPlugin.ALLOW_REPUTATION_LOSS_EVEN_IF_ALREADY_NEGATIVE && relToPlayer <= 0) return 0;

        return relToEnemy > -0.5f || enemyRelToPlayer <= 0.1 ? 0f : (relToPlayer + enemyRelToPlayer) * -relToEnemy;
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            EngagementResultForFleetAPI pf = result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();

            Map<FleetMemberAPI, Float> playerDeployedFP = new HashMap();

            for(FleetMemberAPI fm : pf.getDeployed()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm, true));
            for(FleetMemberAPI fm : pf.getRetreated()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm, true));
            for(FleetMemberAPI fm : pf.getDisabled()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm, true));
            for(FleetMemberAPI fm : pf.getDestroyed()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm, true));
            
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


//        LocationAPI cl = pf.getContainingLocation();
//        Vector2f loc = pf.getLocation();
//
//
//        Global.getSector().getCampaignUI().addMessage(" Speed:" + pf.getCurrBurnLevel());
//
//        CustomCampaignEntityAPI splody = cl.addCustomEntity(Misc.genUID(), null, "sun_rs_lux_wisp", null);
//        splody.setLocation(loc.x, loc.y);


//        float size = pf.getRadius() + 200f;
//        Color color = new Color(100, 255, 150, 255);
//
//        ExplosionEntityPlugin.ExplosionParams params = new ExplosionEntityPlugin.ExplosionParams(color, cl, loc, size, 2f);
//        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.HIGH;
//
//        CustomCampaignEntityAPI splody = cl.addCustomEntity(Misc.genUID(), "Gate Explosion",
//                Entities.EXPLOSION, Factions.NEUTRAL, params);
//        splody.setLocation(loc.x, loc.y);
//
//
//
//        SectorEntityToken focus = pf.getStarSystem().getStar();
//        BaseThemeGenerator.EntityLocation el = new BaseThemeGenerator.EntityLocation();
//        float radius = focus.getRadius() + 100f;
//        el.orbit = Global.getFactory().createCircularOrbit(focus, (float) Math.random() * 360f,
//                radius, radius / (10f + 10f * (float) Math.random()));
//        BaseThemeGenerator.addNonSalvageEntity(cl, el, Entities.FUSION_LAMP, pf.getFaction().getId());






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

            if(distanceFromCore > 0 && !Misc.isInAbyss(at)) {
                at = Misc.getPointAtRadius(at, 3500);

                spawnRemnantFleets(distanceFromCore, at, 250, true);
            }
        }
    }
}