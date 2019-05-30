package ruthless_sector;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ruthless_sector.campaign.CampaignPlugin;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModPlugin extends BaseModPlugin {
    public static final String SETTINGS_PATH = "RUTHLESS_SECTOR_OPTIONS.ini";
    public static final String FACTION_WL_PATH = "data/config/ruthlesssector/faction_rep_change_whitelist.csv";
    public static final String FACTION_BL_PATH = "data/config/ruthlesssector/faction_rep_change_blacklist.csv";
    public static final String COMMON_DATA_PATH = "sun_rs/reload_penalty_record.json";

    public static boolean
            ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = true,
            SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = true,
            SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = true,
            LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = true,
            SHOW_XP_LOSS_NOTIFICATION = true,
            GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = true,
            RESTRICT_REP_GAIN_TO_WHITLISTED_FACTIONS = true;

    public static float
            DIFFICULTY_MULTIPLIER_EXPONENT = 2,
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
            MAX_ENEMY_FLEET_STRENGTH_ESTIMATION = 1.5f,
            LOOTED_CREDITS_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = 0.5f,
            RANGE_MULT_FOR_AUTOMATED_DEFENSES = 2.0f;

    static List<FactionAPI> allowedFactions = new ArrayList();
    static JSONObject commonData;

    static Saved<Double> difficultyMultiplierForLastBattle = new Saved<>("difficultyMultiplierForLastBattle", 0.0);
    static Saved<Double> playerFleetStrengthInLastBattle = new Saved<>("playerFleetStrengthInLastBattle", 0.0);
    static Saved<Double> enemyFleetStrengthInLastBattle = new Saved<>("enemyFleetStrengthInLastBattle", 0.0);

    static CampaignScript script;
    static boolean settingsAreRead = false;
    static int battlesResolvedSinceLastSave = 0;
    static String saveGameID;

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            battlesResolvedSinceLastSave = 0;
            saveGameID = Global.getSector().getPlayerPerson().getId();

            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            script.updateDangerOfAllFleetsAtPlayerLocation();

            Saved.loadPersistentData();

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

            adjustReloadPenalty(battlesResolvedSinceLastSave
                    * -(RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE + RELOAD_PENALTY_PER_RELOAD));
            battlesResolvedSinceLastSave = 0;
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

            JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);

            GalatianAcademyStipend.DURATION = (float)cfg.getDouble("galatianStipendDuration");
            GalatianAcademyStipend.STIPEND = cfg.getInt("galatianStipendPay");

            ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = cfg.getBoolean("enableRemnantEncountersInHyperspace");
            SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = cfg.getBoolean("scaleXpGainBasedOnBattleDifficulty");
            SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = cfg.getBoolean("showBattleDifficultyNotification");
            LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = cfg.getBoolean("loseProgressTowardNextLevelOnDeath");
            SHOW_XP_LOSS_NOTIFICATION = cfg.getBoolean("showXpLossNotification");
            GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = cfg.getBoolean("gainReputationForImpressiveVictories");
            RESTRICT_REP_GAIN_TO_WHITLISTED_FACTIONS = cfg.getBoolean("restrictRepGainToWhitlistedFactions");

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
            MAX_ENEMY_FLEET_STRENGTH_ESTIMATION = (float)cfg.getDouble("maxEnemyFleetStrengthEstimation");

            LOOTED_CREDITS_MULTIPLIER = (float)cfg.getDouble("lootedCreditsMultiplier");
            LOOTED_SALVAGE_MULTIPLIER = (float)cfg.getDouble("lootedSalvageMultiplier");
            LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = (float)cfg.getDouble("lootedSalvageFromRemnantsMultiplier");

            RANGE_MULT_FOR_AUTOMATED_DEFENSES = (float)cfg.getDouble("rangeMultForAutomatedDefenses");


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
                stackTrace += "    " + exception.getStackTrace()[i].toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                Global.getSector().getCampaignUI().addMessage(message, Color.RED);
                Global.getSector().getCampaignUI().addMessage(exception.getMessage(), Color.ORANGE);
                Global.getSector().getCampaignUI().showConfirmDialog(message + "\n\n"
                        + exception.getMessage(), "Ok", null, null, null);
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static double getReloadPenalty() throws IOException, JSONException {
        return commonData.has(saveGameID) ? commonData.getDouble(saveGameID) : 0;
    }
    static void adjustReloadPenalty(float adjustment) throws IOException, JSONException {
        Global.getLogger(ModPlugin.class).info("Reload Penalty Adjustment: " + getReloadPenalty() + " + " + adjustment);

        commonData.put(saveGameID, Math.max(0, Math.min(RELOAD_PENALTY_LIMIT + RELOAD_PENALTY_PER_RELOAD,
                getReloadPenalty() + adjustment)));

        Global.getSettings().writeTextFileToCommon(COMMON_DATA_PATH, commonData.toString());
    }

    public static List<FactionAPI> getAllowedFactions() { return allowedFactions; }

    public static double getDifficultyMultiplierForLastBattle() {
        return difficultyMultiplierForLastBattle.val;
    }
    public static double getPlayerFleetStrengthInLastBattle() { return playerFleetStrengthInLastBattle.val; }
    public static double getEnemyFleetStrengthInLastBattle() {
        return enemyFleetStrengthInLastBattle.val;
    }

    public static void resetIntegrationValues() {
        ModPlugin.difficultyMultiplierForLastBattle.val = 0.0;
        ModPlugin.playerFleetStrengthInLastBattle.val = 0.0;
        ModPlugin.enemyFleetStrengthInLastBattle.val = 0.0;
    }
}
