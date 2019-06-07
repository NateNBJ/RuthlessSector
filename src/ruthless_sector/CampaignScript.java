package ruthless_sector;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.Misc;

import java.util.LinkedList;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float CORE_RADIUS = 16000;
    static final float DANGER_UPDATE_PERIOD = 3; // In seconds
    static final float DANGER_UPDATE_RANGE = 5000;
    static final int MAX_REMNANT_FLEETS = 3;

    static boolean playerJustRespawned = false;

    Saved<Float> distanceToNextEncounter = new Saved("distanceToNextEncounter", 100f);
    Saved<LinkedList<CampaignFleetAPI>> remnantFleets = new Saved<>("remnantFleets", new LinkedList<CampaignFleetAPI>());

    CampaignFleetAPI pf;
    Random random = new Random();
    float messageDelay = 0.5f, timeUntilNextDangerUpdate = DANGER_UPDATE_PERIOD * 0.5695f;
    double pfStrength;

    public CampaignScript() { super(true); }

    @Override
    public void advance(float amount) {
        try {
            if(!ModPlugin.readSettingsIfNecessary()) return;

            pf = Global.getSector().getPlayerFleet();

            if(pf == null) return;

            if(messageDelay != Float.MIN_VALUE && (messageDelay -= amount) <= 0) {
                double penalty = Math.min(1, ModPlugin.getReloadPenalty());

                Global.getLogger(ModPlugin.class).info("Reload Penalty: " + penalty);

                if(penalty > 0) {
                    Global.getSector().getCampaignUI().addMessage("Difficulty rating for the next battle will be reduced by "
                            + Misc.getRoundedValue((float) penalty * 100) + "% due to reloading after battle.", Misc.getNegativeHighlightColor());
                }

                updateDangerOfAllFleetsAtPlayerLocation();

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

            timeUntilNextDangerUpdate -= amount;

            if(timeUntilNextDangerUpdate <= 0) {
                updateDangerOfAllFleetsAtPlayerLocation(DANGER_UPDATE_RANGE);
                timeUntilNextDangerUpdate += DANGER_UPDATE_PERIOD;
            }

            if(Global.getSector().isPaused()) amount = 0;
            else if(Global.getSector().isInFastAdvance()) amount *= 2;

            if(!Global.getSector().isPaused()) {
                float distanceFromCore = pf.getLocation().length() - CORE_RADIUS;

                if (ModPlugin.ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE && pf.isInHyperspace() && distanceFromCore > 0) {
                    distanceToNextEncounter.val -= amount * pf.getCurrBurnLevel();
                }

                if (distanceToNextEncounter.val <= 0) {
                    float d = ModPlugin.AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS;
                    distanceToNextEncounter.val = d * 0.5f + random.nextFloat() * d;
                    spawnRemnantFleet(distanceFromCore);
                }

                ModPlugin.resetIntegrationValues();
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    void spawnRemnantFleet(float distanceFromCore) {
        CampaignFleetAPI fleet = null;
        float maxCombatPoints = ModPlugin.MAX_HYPERSPACE_REMNANT_STRENGTH,
                sectorInnerRadius = Global.getSettings().getFloat("sectorHeight") * 0.5f,
                powerScale = Math.min(1, distanceFromCore / (sectorInnerRadius - CORE_RADIUS));

        log("Spawning remnant fleet with " + (int)(powerScale * 100) + "% of max power");

        purgeOldestRemnantFleetsIfNeeded();

        while(fleet == null) {
            int combatPoints = 1 + random.nextInt((int)Math.max(1, maxCombatPoints * powerScale));

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
            params.withOfficers = false;
            params.random = random;

            fleet = FleetFactoryV3.createFleet(params);
        }

        remnantFleets.val.add(fleet);
        pf.getContainingLocation().addEntity(fleet);
        RemnantSeededFleetManager.initRemnantFleetProperties(random, fleet, false);
        Vector2f loc = new Vector2f(pf.getLocation());
        Vector2f.add(loc, (Vector2f) pf.getVelocity().normalise().scale(2800), loc);
        Vector2f.add(loc, Misc.getPointAtRadius(new Vector2f(), 800f, random), loc);
        fleet.setLocation(loc.x, loc.y);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
        fleet.setFacing(random.nextFloat() * 360f);
        despawnOrResetAssignment(fleet);

        fleet.setTransponderOn(true);
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
            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, null, Float.MAX_VALUE, "waiting", new Script() {
                @Override
                public void run() { }
            });
        }
    }

    public static void setPlayerJustRespawned() { playerJustRespawned = true; }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return !ModPlugin.settingsAreRead || messageDelay > 0; }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        for(CampaignFleetAPI fleet : battle.getBothSides()) updateDangerIfAtPlayerLocation(fleet);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        if(fleet == Global.getSector().getPlayerFleet()) {
            updateDangerOfAllFleetsAtPlayerLocation();
        } else updateDangerIfAtPlayerLocation(fleet, to.getDestination().getContainingLocation());
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        updateDangerIfAtPlayerLocation(fleet);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        updateDangerOfAllFleetsAtPlayerLocation();
    }

    void updateDangerOfAllFleetsAtPlayerLocation() {
        updateDangerOfAllFleetsAtPlayerLocation(Float.MAX_VALUE);
    }
    void updateDangerOfAllFleetsAtPlayerLocation(float maxDistance) {
        try {
            CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
            pfStrength = BattleListener.tallyShipStrength(pf.getFleetData().getMembersListCopy(), true, false);

            for(CampaignFleetAPI f : Misc.getNearbyFleets(pf, maxDistance)) {
                if(f != pf) updateDangerIfAtPlayerLocation(f);
            }
        } catch (Exception e) {
            log(e.getMessage());
        }
    }

    void updateDangerIfAtPlayerLocation(CampaignFleetAPI fleet) {
        updateDangerIfAtPlayerLocation(fleet, fleet.getContainingLocation());
    }
    void updateDangerIfAtPlayerLocation(CampaignFleetAPI fleet, LocationAPI at) {
        try {
            pf = Global.getSector().getPlayerFleet();

            if((fleet.isInCurrentLocation() || at == pf.getContainingLocation()) && fleet != pf) updateDanger(fleet);
        } catch (Exception e) {
            log(e.getMessage());
        }
    }
    void updateDanger(CampaignFleetAPI fleet) {
        //fleet.inflateIfNeeded();

        double strength = BattleListener.tallyShipStrength(fleet.getFleetData().getMembersListCopy(), false, false);
        double ratio = strength / pfStrength;
        int danger = 10;

        if(ratio < 0.50) danger = 1;
        else if(ratio < 0.75) danger = 2;
        else if(ratio < 1.00) danger = 3;
        else if(ratio < 1.25) danger = 4;
        else if(ratio < 1.50) danger = 5;
        else if(ratio < 2.00) danger = 6;
        else if(ratio < 3.00) danger = 7;
        else if(ratio < 4.00) danger = 8;
        else if(ratio < 5.00) danger = 9;

//                if(ratio < 0.5) danger = 1;
//                else if(ratio < 1.00) danger = 2;
//                else if(ratio < 1.50) danger = 3;
//                else if(ratio < 2.00) danger = 4;
//                else danger = 5;

        fleet.getMemoryWithoutUpdate().set("$dangerLevelOverride", danger);
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
}