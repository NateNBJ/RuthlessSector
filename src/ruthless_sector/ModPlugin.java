package ruthless_sector;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaSettings.LunaSettings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ruthless_sector.campaign.CampaignPlugin;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class ModPlugin extends BaseModPlugin {
    public static final String
            PREFIX = "sun_rs_",
            ID = "sun_ruthless_sector",
            SETTINGS_PATH = "RUTHLESS_SECTOR_OPTIONS.ini",
            FACTION_WL_PATH = "data/config/ruthlesssector/faction_rep_change_whitelist.csv",
            FACTION_BL_PATH = "data/config/ruthlesssector/faction_rep_change_blacklist.csv",
            COMMON_DATA_PATH = "sun_rs/reload_penalty_record.json",
            BOUNTY_KEY = "factionCommissionBounty",
            STIPEND_BASE_KEY = "factionCommissionStipendBase",
            STIPEND_PER_LEVEL_KEY = "factionCommissionStipendPerLevel";
    public static final double TIMESTAMP_TICKS_PER_DAY = 8.64E7D;

    static final String LUNALIB_ID = "lunalib";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            id = PREFIX + id;
            
            if(type == Integer.class) return type.cast(LunaSettings.getInt(ModPlugin.ID, id));
            if(type == Float.class) return type.cast(LunaSettings.getFloat(ModPlugin.ID, id));
            if(type == Boolean.class) return type.cast(LunaSettings.getBoolean(ModPlugin.ID, id));
            if(type == Double.class) return type.cast(LunaSettings.getDouble(ModPlugin.ID, id));
            if(type == String.class) return type.cast(LunaSettings.getString(ModPlugin.ID, id));
        } else {
            if (settingsCfg == null) settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            if (type == Integer.class) return type.cast(settingsCfg.getInt(id));
            if (type == Float.class) return type.cast((float) settingsCfg.getDouble(id));
            if (type == Boolean.class) return type.cast(settingsCfg.getBoolean(id));
            if (type == Double.class) return type.cast(settingsCfg.getDouble(id));
            if (type == String.class) return type.cast(settingsCfg.getString(id));
        }

        throw new MissingResourceException("No setting found with id: " + id, type.getName(), id);
    }
    static int getInt(String id) throws Exception { return get(id, Integer.class); }
    static double getDouble(String id) throws Exception { return get(id, Double.class); }
    static float getFloat(String id) throws Exception { return get(id, Float.class); }
    static boolean getBoolean(String id) throws Exception { return get(id, Boolean.class); }
    static String getString(String id) throws Exception { return get(id, String.class); }
    static boolean readSettings() throws Exception {
        GalatianAcademyStipend.DURATION = getFloat("galatianStipendDuration");
        GalatianAcademyStipend.STIPEND = getInt("galatianStipendPay");

        ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = getBoolean("enableRemnantEncountersInHyperspace");
        SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = getBoolean("scaleXpGainBasedOnBattleDifficulty");
        SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = getBoolean("showBattleDifficultyNotification");
        LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = getBoolean("loseProgressTowardNextLevelOnDeath");
        SHOW_XP_LOSS_NOTIFICATION = getBoolean("showXpLossNotification");

        GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = getBoolean("gainReputationForImpressiveVictories");
        RESTRICT_REP_CHANGES_TO_WHITELISTED_FACTIONS = getBoolean("restrictRepChangesToWhitelistedFactions");
        OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY = getBoolean("overrideDangerIndicatorsToShowBattleDifficulty");
        DISABLE_VANILLA_DIFFICULTY_BONUS = getBoolean("disableVanillaDifficultyBonus");
        LOSE_REPUTATION_FOR_BEING_FRIENDLY_WITH_ENEMIES = getBoolean("loseReputationForBeingFriendlyWithEnemies");
        SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN = getBoolean("showBattleDifficultyStarsOnDeploymentScreen");
        ALLOW_REPUTATION_LOSS_EVEN_IF_ALREADY_NEGATIVE = getBoolean("allowReputationLossEvenIfAlreadyNegative");
        MAX_REP_LOSS = getFloat("maxRepLoss");

        float lyPerSecAtOneBL = Misc.getLYPerDayAtBurn(null, 1) / Global.getSector().getClock().getSecondsPerDay();
        AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS = getFloat("averageLightyearsBetweenRemnantEncounters") / lyPerSecAtOneBL;

        MAX_HYPERSPACE_REMNANT_STRENGTH = getFloat("maxHyperspaceRemnantStrength");
        MAX_REP_GAIN = getFloat("maxRepGain");
        CHANCE_OF_ADDITIONAL_HYPERSPACE_REMNANT_FLEETS = getFloat("chanceOfAdditionalHyperspaceRemnantFleets");
        MAX_HYPERSPACE_REMNANT_FLEETS_TO_SPAWN_AT_ONCE = getInt("maxHyperspaceRemnantFleetsToSpawnAtOnce");

        RELOAD_PENALTY_PER_RELOAD = getFloat("reloadPenaltyPerReload");
        RELOAD_PENALTY_LIMIT = getFloat("reloadPenaltyLimit");
        RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE = getFloat("reloadPenaltyReductionPerResolvedBattle");
        RELOAD_PENALTY_REDUCTION_PER_DAY = getFloat("reloadPenaltyReductionPerDay");

        MIN_DIFFICULTY_TO_EARN_XP = getFloat("minDifficultyToEarnXp");
        XP_MULTIPLIER_AFTER_REDUCTION = getFloat("xpMultiplierAfterReduction");
        MAX_XP_MULTIPLIER = (float)getXpMultiplierForDifficulty(2.5); // 2.5 is the last 5 star difficulty

        DMOD_FACTOR_FOR_PLAYER_SHIPS = getFloat("dModFactorForPlayerShips");
        SMOD_FACTOR_FOR_PLAYER_SHIPS = getFloat("sModFactorForPlayerShips");
        SKILL_FACTOR_FOR_PLAYER_SHIPS = getFloat("skillFactorForPlayerShips");
        DMOD_FACTOR_FOR_ENEMY_SHIPS = getFloat("dModFactorForEnemyShips");
        SMOD_FACTOR_FOR_ENEMY_SHIPS = getFloat("sModFactorForEnemyShips");
        SKILL_FACTOR_FOR_ENEMY_SHIPS = getFloat("skillFactorForEnemyShips");
        STRENGTH_INCREASE_PER_PLAYER_LEVEL = getFloat("strengthIncreasePerPlayerLevel");

//            DIFFICULTY_MULTIPLIER_EXPONENT = getFloat("battleDifficultyExponent");
//            ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = getFloat("enemyOfficerIncreaseToShipStrengthPerLevel");
//            ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = getFloat("allyOfficerIncreaseToShipStrengthPerLevel");
//            PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL = getFloat("playerIncreaseToFleetStrengthPerLevel");
//            PLAYER_FLEET_STRENGTH_MULT = getFloat("playerFleetStrengthMult");
//            MAX_BATTLE_DIFFICULTY_ESTIMATION = getFloat("maxBattleDifficultyEstimation");

        LOOTED_CREDITS_MULTIPLIER = getFloat("lootedCreditsMultiplier");
        LOOTED_SALVAGE_MULTIPLIER = getFloat("lootedSalvageMultiplier");
        LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = getFloat("lootedSalvageFromRemnantsMultiplier");

        RANGE_MULT_FOR_AUTOMATED_DEFENSES = getFloat("rangeMultForAutomatedDefenses");
        MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES = getFloat("maxEcmRatingForAutomatedDefenses");
        FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES = getFloat("flatEcmBonusForAutomatedDefenses");

        ENABLE_STARTING_REP_OVERRIDES = getBoolean("enableStartingReputationOverrides");
        STARTING_REPUTATION_OVERRIDE = getFloat("startingReputationOverride");
        OVERRIDE_STARTING_FACTION_REPUTATION_AT_START = getBoolean("overrideStartingFactionReputationAtStart");
        OVERRIDE_INDEPENDENTS_REPUTATION_AT_START = getBoolean("overrideIndependentsReputationAtStart");
        OVERRIDE_PIRATES_REPUTATION_AT_START = getBoolean("overridePiratesReputationAtStart");
        OVERRIDE_REPUTATIONS_OF_OTHER_KNOWN_FACTIONS_AT_START = getBoolean("overrideReputationsOfOtherKnownFactionsAtStart");

        Global.getSettings().setFloat(BOUNTY_KEY, ORIGINAL_BOUNTY * getFloat(BOUNTY_KEY + "Mult"));
        Global.getSettings().setFloat(STIPEND_BASE_KEY, ORIGINAL_STIPEND_BASE * getFloat(STIPEND_BASE_KEY + "Mult"));
        Global.getSettings().setFloat(STIPEND_PER_LEVEL_KEY, ORIGINAL_STIPEND_PER_LEVEL * getFloat(STIPEND_PER_LEVEL_KEY + "Mult"));

        if(isNewGameStartedPriorToSettingsBeingRead && ENABLE_STARTING_REP_OVERRIDES) {
                CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
                LocationAPI startLoc = pf.getContainingLocation();
                String startingFactionID = Factions.NEUTRAL;

                if(Misc.getCommissionFactionId() != null) {
                    startingFactionID = Misc.getCommissionFactionId();
                } else if(!startLoc.isHyperspace()) {
                    float shortestDistanceSoFar = Float.MAX_VALUE;

                    for(MarketAPI mkt : Misc.getMarketsInLocation(startLoc)) {
                        if(mkt.getPrimaryEntity() == null || mkt.getFactionId() == null) continue;

                        float distance = Misc.getDistance(pf, mkt.getPrimaryEntity());

                        if(distance < shortestDistanceSoFar) {
                            startingFactionID = mkt.getFactionId();
                            shortestDistanceSoFar = distance;
                        }
                    }
                }


                for (FactionAPI faction : getAllowedFactions()) {
                    if (faction.isShowInIntelTab()) {
                        if((faction.getId().equals(startingFactionID) && !OVERRIDE_STARTING_FACTION_REPUTATION_AT_START)) {
                            continue;
                        }

                        switch (faction.getId()) {
                            case Factions.INDEPENDENT: if(!OVERRIDE_INDEPENDENTS_REPUTATION_AT_START) continue; break;
                            case Factions.PIRATES: if(!OVERRIDE_PIRATES_REPUTATION_AT_START) continue; break;
                            default: if(!OVERRIDE_REPUTATIONS_OF_OTHER_KNOWN_FACTIONS_AT_START) continue; break;
                        }

                        faction.setRelationship(Factions.PLAYER, STARTING_REPUTATION_OVERRIDE * 0.01f);
                    }
                }
            }

        return true;
    }

    public static boolean
            ENABLE_STARTING_REP_OVERRIDES = false,
            ENABLE_REMNANT_ENCOUNTERS_IN_HYPERSPACE = true,
            SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY = true,
            SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION = true,
            LOSE_PROGRESS_TOWARD_NEXT_LEVEL_ON_DEATH = true,
            SHOW_XP_LOSS_NOTIFICATION = true,
            GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES = true,
            RESTRICT_REP_CHANGES_TO_WHITELISTED_FACTIONS = true,
            OVERRIDE_DANGER_INDICATORS_TO_SHOW_BATTLE_DIFFICULTY = true,
            DISABLE_VANILLA_DIFFICULTY_BONUS = true,
            LOSE_REPUTATION_FOR_BEING_FRIENDLY_WITH_ENEMIES = true,
            SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN = true,
            OVERRIDE_STARTING_FACTION_REPUTATION_AT_START = false,
            OVERRIDE_INDEPENDENTS_REPUTATION_AT_START = false,
            OVERRIDE_PIRATES_REPUTATION_AT_START = false,
            OVERRIDE_REPUTATIONS_OF_OTHER_KNOWN_FACTIONS_AT_START = true,
            ALLOW_REPUTATION_LOSS_EVEN_IF_ALREADY_NEGATIVE = true;

    public static float
            ORIGINAL_BOUNTY = 300,
            ORIGINAL_STIPEND_BASE = 5000,
            ORIGINAL_STIPEND_PER_LEVEL = 1500,

            STARTING_REPUTATION_OVERRIDE = -65,

            AVERAGE_DISTANCE_BETWEEN_REMNANT_ENCOUNTERS = 1600,
            MAX_HYPERSPACE_REMNANT_STRENGTH = 20,
            MAX_REP_GAIN = 5,
            RELOAD_PENALTY_PER_RELOAD = 0.2f,
            RELOAD_PENALTY_LIMIT = 0.8f,
            RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE = 1.0f,
            RELOAD_PENALTY_REDUCTION_PER_DAY = 0.02f,

            MIN_DIFFICULTY_TO_EARN_XP = 0.5f,
            XP_MULTIPLIER_AFTER_REDUCTION = 3.0f,
            MAX_XP_MULTIPLIER = Float.MAX_VALUE,

            DMOD_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            SMOD_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            SKILL_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            DMOD_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            SMOD_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            SKILL_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            STRENGTH_INCREASE_PER_PLAYER_LEVEL = 0.07f,

            //DIFFICULTY_MULTIPLIER_EXPONENT = 1,
            //ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = 0.05f,
            //ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL = 0.05f,
            //PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL = 0.02f,
            //PLAYER_FLEET_STRENGTH_MULT = 1,
            //MAX_BATTLE_DIFFICULTY_ESTIMATION = 1.5f,

            LOOTED_CREDITS_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_MULTIPLIER = 1.0f,
            LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER = 0.5f,
            RANGE_MULT_FOR_AUTOMATED_DEFENSES = 1.5f,
            MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES = 25f,
            FLAT_ECM_BONUS_FOR_AUTOMATED_DEFENSES = 15f,
            CHANCE_OF_ADDITIONAL_HYPERSPACE_REMNANT_FLEETS = 0.4f,
            MAX_REP_LOSS = 10f;

    public static int
            MAX_HYPERSPACE_REMNANT_FLEETS_TO_SPAWN_AT_ONCE = 3;

    static List<FactionAPI> allowedFactions = new ArrayList();
    static JSONObject commonData;

    static Saved<Double> battleDifficulty = new Saved<>("difficultyMultiplierForLastBattle", 0.0);
    static Saved<Double> playerStrength = new Saved<>("playerFleetStrengthInLastBattle", 0.0);
    static Saved<Double> enemyStrength = new Saved<>("enemyFleetStrengthInLastBattle", 0.0);

    static CampaignScript script;
    static boolean settingsAreRead = false;
    static boolean battleStartedSinceLastSave = false;
    static boolean isNewGameStartedPriorToSettingsBeingRead = false;
    static int battlesResolvedSinceLastSave = 0;
    static long timeOfLastSave = 0;
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
            if(!ignoreRC && !other.isOlderThan(this, true) && RC < other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }

    void removeScripts() {
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
        Global.getSector().removeScriptsOfClass(CampaignScript.class);
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

            ORIGINAL_BOUNTY = Global.getSettings().getFloat(BOUNTY_KEY);
            ORIGINAL_STIPEND_BASE = Global.getSettings().getFloat(STIPEND_BASE_KEY);
            ORIGINAL_STIPEND_PER_LEVEL = Global.getSettings().getFloat(STIPEND_PER_LEVEL_KEY);

            readSettingsIfNecessary(true);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new Exception(message);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            removeScripts();

            isNewGameStartedPriorToSettingsBeingRead = newGame;
            battlesResolvedSinceLastSave = 0;
            timeOfLastSave = Global.getSector().getClock().getTimestamp();
            battleStartedSinceLastSave = false;
            saveGameID = Global.getSector().getPlayerPerson().getId();
            allowedFactions.clear();

            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();

            CombatPlugin.clearDomainDroneEcmBonusFlag();

            if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
                LunaSettingsChangedListener.addToManagerIfNeeded();
            }

            readSettingsIfNecessary(true);
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void beforeGameSave() {
        try {
            Saved.updatePersistentData();
            removeScripts();

            if(battlesResolvedSinceLastSave > 0 || Global.getSector().getClock().getElapsedDaysSince(timeOfLastSave) > 0) {
                adjustReloadPenalty((battlesResolvedSinceLastSave > 0 ? -RELOAD_PENALTY_PER_RELOAD : 0)
                        - battlesResolvedSinceLastSave * RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE
                        - Global.getSector().getClock().getElapsedDaysSince(timeOfLastSave) * RELOAD_PENALTY_REDUCTION_PER_DAY);
                battlesResolvedSinceLastSave = 0;
                timeOfLastSave = Global.getSector().getClock().getTimestamp();
            }

            battleStartedSinceLastSave = false;

        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    static boolean readSettingsIfNecessary(boolean forceRefresh) {
        try {
            if(forceRefresh) settingsAreRead = false;

            if(settingsAreRead) return true;

            if(Global.getSector() == null || Global.getSector().getAllFactions() == null) return false;

            try {
                commonData = new JSONObject(Global.getSettings().readTextFileFromCommon(COMMON_DATA_PATH));
            } catch (JSONException e) {
                Global.getSettings().writeTextFileToCommon(COMMON_DATA_PATH, "{}");
                commonData = new JSONObject(Global.getSettings().readTextFileFromCommon(COMMON_DATA_PATH));
            }
            
            Set<String> bl = fetchList(FACTION_BL_PATH);
            Set<String> wl = RESTRICT_REP_CHANGES_TO_WHITELISTED_FACTIONS ? fetchList(FACTION_WL_PATH) : null;

            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (faction.isShowInIntelTab()
                        && (bl == null || !bl.contains(faction.getId()))
                        && (wl == null || wl.contains(faction.getId()))) {

                    allowedFactions.add(faction);
                }
            }

            readSettings();

            isNewGameStartedPriorToSettingsBeingRead = false;

            return settingsAreRead = true;
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }

    static Set<String> fetchList(String path) throws IOException, JSONException {
        Set<String> list = new HashSet<>();
        JSONArray afJsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("faction id", path, ID);

        for (int i = 0; i < afJsonArray.length(); i++) {
                //Global.getLogger(this.getClass()).info(i + " : " + afJsonArray.getJSONObject(i).getString("faction id"));

                list.add(afJsonArray.getJSONObject(i).getString("faction id"));
            }

        return list;
    }

    public static boolean reportCrash(Exception exception) {
        return reportCrash(exception, true);
    }
    public static boolean reportCrash(Exception exception, boolean displayToUser) {
        try {
            String stackTrace = "", message = "Ruthless Sector encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (!displayToUser) {
                return true;
            } else if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
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

    public static double tallyShipStrength(Collection<FleetMemberAPI> fleet, boolean isPlayerFleet) {
        float fpTotal = 0;

        for (FleetMemberAPI ship : fleet) fpTotal += getShipStrength(ship, isPlayerFleet);

        return Math.max(1, fpTotal);
    }
    public static float getShipStrength(FleetMemberAPI ship, boolean isPlayerShip) {
        float fp = ship.getFleetPointCost();
        float strength;

        if(ship.isFighterWing() || !ship.canBeDeployedForCombat() || ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed()) {
            strength = 0;
        } else if(ship.isStation()) {
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

            strength = fp * (totalOP - detachedOP) / Math.max(1, totalOP);
        } else {
            //boolean isPlayerShip = ship.getOwner() == 0 && !ship.isAlly();
            float dModFactor = isPlayerShip ? ModPlugin.DMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.DMOD_FACTOR_FOR_ENEMY_SHIPS;
            float sModFactor = isPlayerShip ? ModPlugin.SMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SMOD_FACTOR_FOR_ENEMY_SHIPS;
            float skillFactor = isPlayerShip ? ModPlugin.SKILL_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SKILL_FACTOR_FOR_ENEMY_SHIPS;

            float dMods = DModManager.getNumDMods(ship.getVariant());
            float sMods = ship.getVariant().getSMods().size();
            float skills = 0;
            PersonAPI captain = ship.getCaptain();

            if(captain != null && !captain.isDefault()) {
                for(MutableCharacterStatsAPI.SkillLevelAPI skill : captain.getStats().getSkillsCopy()) {
                    if (skill.getSkill().isCombatOfficerSkill()) {
                        if(skill.getLevel() > 0) skills += skill.getSkill().isElite() ? 1.25f : 1;
                    }
                }
            }

            float dModMult = (float) Math.pow(1 - dModFactor, dMods);
            float sModMult = (float) Math.pow(1 + sModFactor, sMods);
            float skillMult = (float) Math.pow(1 + skillFactor, skills);
            float playerStrengthMult = 1;

            if(isPlayerShip) {
                MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();
                int effectiveLevel = Math.max(0, Math.min(15, stats.getLevel() - stats.getPoints()));

                playerStrengthMult += ModPlugin.STRENGTH_INCREASE_PER_PLAYER_LEVEL * effectiveLevel;
            }

            strength = fp * (1 + (fp - 5f) / 25f) * dModMult * sModMult * skillMult * playerStrengthMult;

//            Global.getLogger(ModPlugin.class).info(String.format("%20s strength: %3.1f = %3.1f * %.2f * %.2f * %.2f * %.2f",
//                    ship.getHullId(), strength, fp * (1 + (fp - 5f) / 25f), dModMult, sModMult, skillMult, playerStrengthMult));
        }

        return strength;
    }
//    public static float getShipStrength(FleetMemberAPI ship) {
//        float strength = ship.getFleetPointCost();
//        float fpPerOfficerLevel = ship.getOwner() == 0
//                ? ModPlugin.ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL
//                : ModPlugin.ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL;
//
//        if(ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed() || ship.isFighterWing() || ship.isCivilian() || !ship.canBeDeployedForCombat()) {
//            return 0;
//        } if(ship.isStation()) {
//            ShipVariantAPI variant = ship.getVariant();
//            List<String> slots = variant.getModuleSlots();
//            float totalOP = 0, detachedOP = 0;
//
//            for(int i = 0; i < slots.size(); ++i) {
//                ShipVariantAPI module = variant.getModuleVariant(slots.get(i));
//                float op = module.getHullSpec().getOrdnancePoints(null);
//
//                totalOP += op;
//
//                if(ship.getStatus().isPermaDetached(i+1)) {
//                    detachedOP += op;
//                }
//            }
//
//            strength *= (totalOP - detachedOP) / Math.max(1, totalOP);
//        } else if(ship.getHullSpec().hasTag("UNBOARDABLE")) {
//            float dModMult = ship.getBaseDeploymentCostSupplies() > 0
//                    ? (ship.getDeploymentCostSupplies() / ship.getBaseDeploymentCostSupplies())
//                    : 1;
//
//            strength *= Math.max(1, Math.min(2, 1 + (strength - 5f) / 25f)) * dModMult;
//        } else{
//            strength = ship.getDeploymentCostSupplies();
//        }
//
//        float captainBonus = (ship.getCaptain() == Global.getSector().getPlayerPerson() || ship.getCaptain().isDefault()) ? 0
//                : ship.getCaptain().getStats().getLevel() * fpPerOfficerLevel;
//
//        if(ship.getOwner() == 0) {
//            float commanderLevel = 0;
//
//            if(ship.getFleetCommanderForStats() != null && ship.getFleetCommanderForStats().getStats() != null) {
//                commanderLevel = ship.getFleetCommanderForStats().getStats().getLevel();
//            } else if(ship.getOwner() == 0) {
//                commanderLevel = Global.getSector().getPlayerStats().getLevel();
//            }
//
//            strength *= ModPlugin.PLAYER_FLEET_STRENGTH_MULT;
//            strength *= 1 + ModPlugin.PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL * commanderLevel;
//        }
//
//        return strength * (1 + captainBonus);
//    }

    static void updateBattleDifficulty() {
        try {
            battleDifficulty.val = enemyStrength.val / playerStrength.val;

//            battleDifficulty.val = Math.pow(enemyStrength.val / playerStrength.val, DIFFICULTY_MULTIPLIER_EXPONENT);
//            battleDifficulty.val = Math.min(battleDifficulty.val, MAX_BATTLE_DIFFICULTY_ESTIMATION);
//            battleDifficulty.val *= Math.max (0, 1f - getReloadPenalty());
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
        penalty -= battlesResolvedSinceLastSave * RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE;
        penalty -= Global.getSector().getClock().getElapsedDaysSince(timeOfLastSave) * RELOAD_PENALTY_REDUCTION_PER_DAY;

        // Compensate for preemptive penalty increase at start of first battle of game session
        penalty -= battleStartedSinceLastSave ? ModPlugin.RELOAD_PENALTY_PER_RELOAD : 0;

        // Clamp
        penalty = Math.max(0, Math.min(RELOAD_PENALTY_LIMIT, penalty));

        return penalty;
    }
    public static double getDifficultyMultiplierForLastBattle() {
        return battleDifficulty.val;
    }
    public static double getDifficultyForLastBattle() {
        return battleDifficulty.val;
    }
    public static double getXpMultiplierForDifficulty(double difficulty) {
        return Math.max(0, Math.min(MAX_XP_MULTIPLIER, (difficulty - MIN_DIFFICULTY_TO_EARN_XP) * XP_MULTIPLIER_AFTER_REDUCTION));
    }
    public static double getXpMultiplierForLastBattle() {
        try {
            double xpMult = getXpMultiplierForDifficulty(battleDifficulty.val);
            return Math.max(0, xpMult * (1f - getReloadPenalty()));
        } catch (Exception e) { reportCrash(e); }

        return 1;
    }
    public static double getPlayerFleetStrengthInLastBattle() { return playerStrength.val; }
    public static double getEnemyFleetStrengthInLastBattle() {
        return enemyStrength.val;
    }
    public static BaseCommandPlugin createSalvageDefenderInteraction() {
        return new ruthless_sector.campaign.rulecmd.RS_SalvageDefenderInteraction();
    }
}
