package ruthless_sector;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ruthless_sector.campaign.CampaignPlugin;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ModPlugin extends BaseModPlugin {
    public static final String
            ID = "sun_ruthless_sector",
            SETTINGS_PATH = "RUTHLESS_SECTOR_OPTIONS.ini",
            FACTION_WL_PATH = "data/config/ruthlesssector/faction_rep_change_whitelist.csv",
            FACTION_BL_PATH = "data/config/ruthlesssector/faction_rep_change_blacklist.csv",
            COMMON_DATA_PATH = "sun_rs/reload_penalty_record.json";

    public static boolean
            ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = true,
            SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = true,
            SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = true,
            LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = true,
            SHOW_XP_LOSS_NOTIFICATION = true,
            GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = true,
            RESTRICT_REP_GAIN_TO_WHITLISTED_FACTIONS = true,
            OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY = true,
            SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN = true;
    public static float
            OFFICER_XP_MULT = 0.5f,
            DIFFICULTY_MULTIPLIER_EXPONENT = 1,
            AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS = 800,
            MAX_HYPERSPACE_REMNANT_STRENGTH = 20,
            MAX_REP_GAIN = 5,
            RELOAD_PENALTY_PER_RELOAD = 0.2f,
            RELOAD_PENALTY_LIMIT = 1,
            RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE = 1.0f,
            ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = 0.05f,
            ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = 0.05f,
            PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL = 0.02f,
            PLAYER_FLEET_STRENGTH_MULT = 1,
            MAX_BATTLE_DIFFICULTY_ESTIMATION = 1.5f,
            LOOTED_CREDITS_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = 0.5f,
            RANGE_MULT_FOR_AUTOMATED_DEFENSES = 1.5f,
            MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES = 25f,
            FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES = 15f;

    static List<FactionAPI> allowedFactions = new ArrayList();
    static JSONObject commonData;

    static Saved<Double> battleDifficulty = new Saved<>("difficultyMultiplierForLastBattle", 0.0);
    static Saved<Double> playerStrength = new Saved<>("playerFleetStrengthInLastBattle", 0.0);
    static Saved<Double> enemyStrength = new Saved<>("enemyFleetStrengthInLastBattle", 0.0);

    static CampaignScript script;
    static boolean settingsAreRead = false, battleStartedSinceLastSave = false;
    static int battlesResolvedSinceLastSave = 0;
    static String saveGameID;

    class Version {
        public final int MAJOR, MINOR, PATCH, RC;

        public Version(String versionStr) {
            String[] temp = versionStr.replace("Starsector ", "").replace("a", "").split("-RC");

            RC = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;

            temp = temp[0].split("\\.");

            MAJOR = temp.length > 0 ? Integer.parseInt(temp[0]) : 0;
            MINOR = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;
            PATCH = temp.length > 2 ? Integer.parseInt(temp[2]) : 0;
        }

        public boolean isOlderThan(Version other, boolean ignoreRC) {
            if(MAJOR < other.MAJOR) return true;
            if(MINOR < other.MINOR) return true;
            if(PATCH < other.PATCH) return true;
            if(!ignoreRC && RC < other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }

    @Override
    public void onApplicationLoad() throws Exception {
        String message = "";

        try {
            ModSpecAPI spec = Global.getSettings().getModManager().getModSpec(ID);
            Version minimumVersion = new Version(spec.getGameVersion());
            Version currentVersion = new Version(Global.getSettings().getVersionString());

            if(currentVersion.isOlderThan(minimumVersion, false)) {
                message = String.format("\rThis version of Starsector is too old for %s!" +
                                "\rPlease make sure Starsector is up to date. (http://fractalsoftworks.com/preorder/)" +
                                "\rMinimum Version: %s" +
                                "\rCurrent Version: %s",
                        spec.getName(), minimumVersion, currentVersion);
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new Exception(message);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            battlesResolvedSinceLastSave = 0;
            battleStartedSinceLastSave = false;
            saveGameID = Global.getSector().getPlayerPerson().getId();

            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            script.updateDangerOfAllFleetsAtPlayerLocation();

            Saved.loadPersistentData();

            CombatPlugin.clearDomainDroneEcmBonusFlag();

            readSettingsIfNecessary();
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void beforeGameSave() {
        try {
            Saved.updatePersistentData();
            Global.getSector().removeTransientScript(script);
            Global.getSector().removeListener(script);
            Global.getSector().removeScriptsOfClass(CampaignScript.class);

            if(battlesResolvedSinceLastSave > 0) {
                adjustReloadPenalty(-RELOAD_PENALTY_PER_RELOAD - battlesResolvedSinceLastSave * RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE );
                battlesResolvedSinceLastSave = 0;
            }

            battleStartedSinceLastSave = false;

        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        script.updateDangerOfAllFleetsAtPlayerLocation();

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            try {
                commonData = new JSONObject(Global.getSettings().readTextFileFromCommon(COMMON_DATA_PATH));
            } catch (JSONException e) {
                Global.getSettings().writeTextFileToCommon(COMMON_DATA_PATH, "{}");
                commonData = new JSONObject(Global.getSettings().readTextFileFromCommon(COMMON_DATA_PATH));
            }

            //JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);
            JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            GalatianAcademyStipend.DURATION = (float)cfg.getDouble("galatianStipendDuration");
            GalatianAcademyStipend.STIPEND = cfg.getInt("galatianStipendPay");

            ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = cfg.getBoolean("enableRemnantEncountersInHyperspace");
            SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = cfg.getBoolean("scaleXpGainBasedOnBattleDifficulty");
            SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = cfg.getBoolean("showBattleDifficultyNotification");
            LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = cfg.getBoolean("loseProgressTowardNextLevelOnDeath");
            SHOW_XP_LOSS_NOTIFICATION = cfg.getBoolean("showXpLossNotification");
            GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = cfg.getBoolean("gainReputationForImpressiveVictories");
            RESTRICT_REP_GAIN_TO_WHITLISTED_FACTIONS = cfg.getBoolean("restrictRepGainToWhitlistedFactions");
            OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY = cfg.getBoolean("overrideDangerIndicatorsToShowBattleDifficulty");
            SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN = cfg.getBoolean("showBattleDifficultyStarsOnDeploymentScreen");

            OFFICER_XP_MULT = (float)cfg.getDouble("officerXpMult");
            AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS = (float)cfg.getDouble("averageDistanceBetweenRemnantEncounters");
            MAX_HYPERSPACE_REMNANT_STRENGTH = (float)cfg.getDouble("maxHyperspaceRemnantStrength");
            MAX_REP_GAIN = (float)cfg.getDouble("maxRepGain");

            RELOAD_PENALTY_PER_RELOAD = (float)cfg.getDouble("reloadPenaltyPerReload");
            RELOAD_PENALTY_LIMIT = (float)cfg.getDouble("reloadPenaltyLimit");
            RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE = (float)cfg.getDouble("reloadPenaltyReductionPerResolvedBattle");

            DIFFICULTY_MULTIPLIER_EXPONENT = (float)cfg.getDouble("battleDifficultyExponent");
            ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = (float)cfg.getDouble("enemyOfficerIncreaseToShipStrengthPerLevel");
            ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = (float)cfg.getDouble("allyOfficerIncreaseToShipStrengthPerLevel");
            PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL = (float)cfg.getDouble("playerIncreaseToFleetStrengthPerLevel");
            PLAYER_FLEET_STRENGTH_MULT = (float)cfg.getDouble("playerFleetStrengthMult");
            MAX_BATTLE_DIFFICULTY_ESTIMATION = (float)cfg.getDouble("maxBattleDifficultyEstimation");

            LOOTED_CREDITS_MULTIPLIER = (float)cfg.getDouble("lootedCreditsMultiplier");
            LOOTED_SALVAGE_MULTIPLIER = (float)cfg.getDouble("lootedSalvageMultiplier");
            LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = (float)cfg.getDouble("lootedSalvageFromRemnantsMultiplier");

            RANGE_MULT_FOR_AUTOMATED_DEFENSES = (float)cfg.getDouble("rangeMultForAutomatedDefenses");
            MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES = (float)cfg.getDouble("maxEcmRatingForAutomatedDefenses");
            FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES = (float)cfg.getDouble("flatEcmBonusForAutomatedDefenses");

            Set<String> bl = fetchList(FACTION_BL_PATH);
            Set<String> wl = cfg.getBoolean("restrictRepGainToWhitlistedFactions")
                    ? fetchList(FACTION_WL_PATH) : null;

            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (faction.isShowInIntelTab()
                        && (bl == null || !bl.contains(faction.getId()))
                        && (wl == null || wl.contains(faction.getId()))) {

                    allowedFactions.add(faction);
                }
            }

            return settingsAreRead = true;
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }

    static Set<String> fetchList(String path) {
        Set<String> list = new HashSet<>();

        try {
            JSONArray afJsonArray = Global.getSettings().loadCSV(path);

            for (int i = 0; i < afJsonArray.length(); i++) {
                //Global.getLogger(this.getClass()).info(i + " : " + afJsonArray.getJSONObject(i).getString("faction id"));

                list.add(afJsonArray.getJSONObject(i).getString("faction id"));
            }
        } catch (Exception e) {
            Global.getLogger(BattleListener.class).error("Error reading " + path, e);
            return null;
        }

        return list;
    }

    public static boolean reportCrash(Exception exception) {
        try {
            String stackTrace = "", message = "Ruthless Sector encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                CampaignUIAPI ui = Global.getSector().getCampaignUI();

                ui.addMessage(message, Color.RED);
                ui.addMessage(exception.getMessage(), Color.ORANGE);
                ui.showConfirmDialog(message + "\n\n" + exception.getMessage(), "Ok", null, null, null);

                if(ui.getCurrentInteractionDialog() != null) ui.getCurrentInteractionDialog().dismiss();
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static List<FactionAPI> getAllowedFactions() { return allowedFactions; }

    public static double tallyShipStrength(Collection<FleetMemberAPI> fleet) {
        float fpTotal = 0;

        for (FleetMemberAPI ship : fleet) fpTotal += getShipStrength(ship);

        return Math.max(1, fpTotal);
    }
    public static float getShipStrength(FleetMemberAPI ship) {
        float strength = ship.getFleetPointCost();
        float fpPerOfficerLevel = ship.getOwner() == 0
                ? ModPlugin.ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL
                : ModPlugin.ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL;

        if(ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed() || ship.isFighterWing() || ship.isCivilian() || !ship.canBeDeployedForCombat()) {
            return 0;
        } if(ship.isStation()) {
            ShipVariantAPI variant = ship.getVariant();
            List<String> slots = variant.getModuleSlots();
            float totalOP = 0, detachedOP = 0;

            for(int i = 0; i < slots.size(); ++i) {
                ShipVariantAPI module = variant.getModuleVariant(slots.get(i));
                float op = module.getHullSpec().getOrdnancePoints(null);

                totalOP += op;

                if(ship.getStatus().isPermaDetached(i+1)) {
                    detachedOP += op;
                }
            }

            //Global.getLogger(ModPlugin.class).info("total OP: " + totalOP + " detached OP: " + detachedOP);

            strength *= (totalOP - detachedOP) / Math.max(1, totalOP);
        } else if(ship.getHullSpec().hasTag("UNBOARDABLE")) {
            float dModMult = ship.getBaseDeploymentCostSupplies() > 0
                    ? (ship.getDeploymentCostSupplies() / ship.getBaseDeploymentCostSupplies())
                    : 1;

            strength *= Math.max(1, Math.min(2, 1 + (strength - 5f) / 25f)) * dModMult;
        } else{
            strength = ship.getDeploymentCostSupplies();
        }

//        if(!slots.isEmpty()) {
//            int allOP = 0, detachedOP = 0;
//
//            for(int i = 0; i < slots.size(); ++i) {
//                String moduleID = ship.getVariant().getStationModules().get(slots.get(i));
//                Global.getLogger(ModPlugin.class).info(moduleID + " Detached: " + ship.getStatus().isPermaDetached(i));
//                int op = Global.getSettings().getVariant(moduleID).getHullSpec().getOrdnancePoints(ship.getCaptain().getStats());
//
//                //Global.getLogger(ModPlugin.class).info(moduleID + " OP: " + op + " Detached: " + ship.getStatus().isPermaDetached(i));
//
//                allOP += op;
//
//                if(ship.getStatus().isPermaDetached(i)) detachedOP += op;
//            }
//
//            strength *= allOP == 0 ? 1 : detachedOP / allOP;
//        }

        float captainBonus = (ship.getCaptain() == Global.getSector().getPlayerPerson() || ship.getCaptain().isDefault()) ? 0
                : ship.getCaptain().getStats().getLevel() * fpPerOfficerLevel;

        if(ship.getOwner() == 0) {
            float commanderLevel = 0;

            if(ship.getFleetCommanderForStats() != null && ship.getFleetCommanderForStats().getStats() != null) {
                commanderLevel = ship.getFleetCommanderForStats().getStats().getLevel();
            } else if(ship.getOwner() == 0) {
                commanderLevel = Global.getSector().getPlayerStats().getLevel();
            }

            strength *= ModPlugin.PLAYER_FLEET_STRENGTH_MULT;
            strength *= 1 + ModPlugin.PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL * commanderLevel;
        }

        return strength * (1 + captainBonus);
    }

    static void updateBattleDifficulty() {
        try {
            battleDifficulty.val = Math.pow(enemyStrength.val / playerStrength.val, DIFFICULTY_MULTIPLIER_EXPONENT);
            battleDifficulty.val = Math.min(battleDifficulty.val, MAX_BATTLE_DIFFICULTY_ESTIMATION);
            battleDifficulty.val *= Math.max (0, 1f - getReloadPenalty());
        } catch (Exception e) { reportCrash(e); }
    }
    static void updatePlayerStrength(double strength) {
        Global.getLogger(ModPlugin.class).info("Player strength: " + strength);
        playerStrength.val = Math.max(1, Math.max(strength, playerStrength.val));

        updateBattleDifficulty();
    }
    static void updateEnemyStrength(double strength) {
        Global.getLogger(ModPlugin.class).info("Enemy strength: " + strength);
        enemyStrength.val = Math.max(1, Math.max(strength, enemyStrength.val));

        updateBattleDifficulty();
    }
    static void adjustReloadPenalty(float adjustment) throws IOException, JSONException {
        double penalty = commonData.has(saveGameID) ? commonData.getDouble(saveGameID) : 0;
        Global.getLogger(ModPlugin.class).info("Reload Penalty Adjustment: " + penalty + " + " + adjustment);

        commonData.put(saveGameID, Math.max(0, Math.min(RELOAD_PENALTY_LIMIT + RELOAD_PENALTY_PER_RELOAD,
                penalty + adjustment)));

        Global.getSettings().writeTextFileToCommon(COMMON_DATA_PATH, commonData.toString());
    }
    static void resetIntegrationValues() {
        ModPlugin.battleDifficulty.val = 0.0;
        ModPlugin.playerStrength.val = 0.0;
        ModPlugin.enemyStrength.val = 0.0;
    }

    public static double getReloadPenalty() throws IOException, JSONException {
        double penalty = commonData.has(saveGameID) ? commonData.getDouble(saveGameID) : 0;

        // Account for unsaved penalty reductions
        penalty -= (battlesResolvedSinceLastSave * RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE);

        // Compensate for preemptive penalty increase at start of first battle of game session
        penalty -= battleStartedSinceLastSave ? ModPlugin.RELOAD_PENALTY_PER_RELOAD : 0;

        // Clamp
        penalty = Math.max(0, Math.min(RELOAD_PENALTY_LIMIT, penalty));

        return penalty;
    }
    public static double getDifficultyMultiplierForLastBattle() {
        return battleDifficulty.val;
    }
    public static double getPlayerFleetStrengthInLastBattle() { return playerStrength.val; }
    public static double getEnemyFleetStrengthInLastBattle() {
        return enemyStrength.val;
    }
}
