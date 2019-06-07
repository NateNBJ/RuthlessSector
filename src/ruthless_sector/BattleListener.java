package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BattleListener {
    static void log(String message) { if(true) Global.getLogger(BattleListener.class).info(message); }

    static boolean isFirstEngagementOfBattle = true, battleInvolvesRemnants = false;
    static double enemyStrength = 0;
    static Set<FleetMemberAPI> deployedPlayerShips = new HashSet<>();
    static BattleAPI battle;
    static FleetEncounterContext context;
    static com.fs.starfarer.api.campaign.InteractionDialogPlugin dialog;

    public static void setVars(FleetEncounterContext context, com.fs.starfarer.api.campaign.InteractionDialogPlugin dialog) {
        BattleListener.context = context;
        BattleListener.dialog = dialog;
    }

    public static void setBattle(BattleAPI battle) {
        try {
            BattleListener.battle = battle;
            battleInvolvesRemnants = false;

            ModPlugin.resetIntegrationValues();

            if(battle == null || battle.getBothSides() == null) return;

            for (CampaignFleetAPI fleet : battle.getBothSides()) {
                if(fleet.getFaction() == null) continue;

                if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
                    battleInvolvesRemnants = true;
                }
            }
        } catch(Exception e) { ModPlugin.reportCrash(e); }
    }

    public static void processEngagementResults(EngagementResultAPI result) {
        try {
            boolean isPursuitEngagement = result.getWinnerResult().getGoal() == FleetGoal.ESCAPE
                    || result.getLoserResult().getGoal() == FleetGoal.ESCAPE;

//            log("Is First Engagement: " + isFirstEngagementOfBattle);
//            log("Is Pursuit Engagement: " + isPursuitEngagement);

            if(enemyStrength != 0 && (!isPursuitEngagement || isFirstEngagementOfBattle)) {
                EngagementResultForFleetAPI pf = result.didPlayerWin()
                        ? result.getWinnerResult()
                        : result.getLoserResult();

                deployedPlayerShips.addAll(pf.getDeployed());
                deployedPlayerShips.addAll(pf.getDestroyed());
                deployedPlayerShips.addAll(pf.getDisabled());
                deployedPlayerShips.addAll(pf.getRetreated());
            }

            isFirstEngagementOfBattle = false;
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    public static float getXpGain(com.fs.starfarer.api.impl.campaign.FleetEncounterContext context, FleetEncounterContextPlugin.DataForEncounterSide side, FleetEncounterContextPlugin.DataForEncounterSide otherSide) {
        try {
            int fpTotal = 0;
            for (FleetEncounterContextPlugin.FleetMemberData data : otherSide.getOwnCasualties()) {
                float fp = data.getMember().getFleetPointCost();
                //float fp = data.getMember().getDeploymentCostSupplies();
                fp *= 1f + data.getMember().getCaptain().getStats().getLevel() / 5f;
                fpTotal += fp;
            }

            float xp = (float) fpTotal * 250;
            xp *= 2f;

            xp *= context.computePlayerContribFraction();

            xp *= Global.getSettings().getFloat("xpGainMult");

            // Vanilla code ends, RS code begins

            float reloadPenalty = (float)ModPlugin.getReloadPenalty()
                    - (ModPlugin.battlesResolvedSinceLastSave * ModPlugin.RELOAD_PENALTY_REDUCTION_PER_RESOLVED_BATTLE)
                    - (ModPlugin.battlesResolvedSinceLastSave += 1) * ModPlugin.RELOAD_PENALTY_PER_RELOAD;

            reloadPenalty = Math.max(0, Math.min(1, reloadPenalty));

            double playerStrength = tallyShipStrength(deployedPlayerShips, true, true);
            float modifier = 0;

            if (xp > 0) {
                if(!deployedPlayerShips.isEmpty() && enemyStrength != 0) {
                    double relativeFleetStrength = Math.min(enemyStrength / Math.max(1, playerStrength),
                            ModPlugin.MAX_ENEMY_FLEET_STRENGTH_ESTIMATION);
                    float rsXp = xp * (1 - reloadPenalty)
                            * (float)Math.pow(relativeFleetStrength, ModPlugin.DIFFICULTY_MULTIPLIER_EXPONENT);
                    modifier = 100f * (rsXp / xp);

                    log("Reload Penalty: " + (int)(reloadPenalty * 100) + "%");
                    log("Modifier: " + modifier + "%");

                    if(ModPlugin.SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY) {
                        log("XP: " + rsXp + ", Vanilla XP: " + xp);

                        xp = rsXp;

                        if (ModPlugin.SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION) {
                            MessageIntel intel = new MessageIntel();
                            intel.addLine("Battle Difficulty: %s", Misc.getTextColor(),
                                    new String[] { (int)modifier + "%" }, Misc.getHighlightColor() );
                            Global.getSector().getCampaignUI().addMessage(intel);

                            if(reloadPenalty > 0) {
                                Global.getSector().getCampaignUI().addMessage("(reduced by "
                                        + Misc.getRoundedValue(reloadPenalty * 100)
                                        + "% due to reloading after battle)", Misc.getHighlightColor());
                            }
                        }
                    }

                    if(ModPlugin.GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES && modifier > 100 && context.didPlayerWinEncounter()) {
                        float repGain = (modifier / 100f - 1f) * 10 * (float)(Math.pow(enemyStrength / 50f, 0.3f) - 0.3f);
                        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker();

                        for(FactionAPI faction : ModPlugin.getAllowedFactions()) {
                            if(faction != otherSide.getFleet().getFaction()
                                    && faction.getRelationship(otherSide.getFleet().getFaction().getId()) <= -0.5f // -0.5 is hostile threshhold
                                    && faction.getRelToPlayer().getLevel() != RepLevel.VENGEFUL
                                    && repGain - Math.max(0, faction.getRelToPlayer().getRel()) * 4f > 0) {

                                picker.add(faction);
                            }
                        }

                        if(!picker.isEmpty()) {
                            FactionAPI faction = picker.pick();

                            repGain -= Math.max(0, faction.getRelToPlayer().getRel()) * 4f;
                            repGain = (float) Math.floor(repGain) + (Math.random() <= repGain - Math.floor(repGain) ? 1 : 0);
                            repGain = Math.min(ModPlugin.MAX_REP_GAIN, repGain);
                            repGain *= 0.01f;

                            log("Rep change of " + repGain + " with " + faction.getId() + " due to impressive victory");

                            if (repGain > 0) {
                                faction.adjustRelationship("player", repGain);
                                CoreReputationPlugin.addAdjustmentMessage(repGain, faction, null,
                                        null, null, null, null, true, 0f, "Change caused by impressive victory");
                            }
                        }
                    }
                }
            }

            ModPlugin.difficultyMultiplierForLastBattle.val = modifier / 100.0;
            ModPlugin.playerFleetStrengthInLastBattle.val = playerStrength;
            ModPlugin.enemyFleetStrengthInLastBattle.val = enemyStrength;

            isFirstEngagementOfBattle = true;
            enemyStrength = 0;

            return xp;
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return 0;
    }

    public static void processSelectedOption(String text, Object optionData) {
        try {
            if(!(optionData instanceof FleetInteractionDialogPluginImpl.OptionId)) return;

            FleetInteractionDialogPluginImpl.OptionId option = (FleetInteractionDialogPluginImpl.OptionId) optionData;

            log(option.toString());

            if(option == FleetInteractionDialogPluginImpl.OptionId.CONTINUE_INTO_BATTLE
                    && ModPlugin.battlesResolvedSinceLastSave == 0 && isFirstEngagementOfBattle) {

                ModPlugin.adjustReloadPenalty(ModPlugin.RELOAD_PENALTY_PER_RELOAD);
            } else if(option == FleetInteractionDialogPluginImpl.OptionId.JOIN_ONGOING_BATTLE
                    || option == FleetInteractionDialogPluginImpl.OptionId.ENGAGE) {

                deployedPlayerShips.clear();
                enemyStrength = 0;

                for (CampaignFleetAPI fleet : battle.getBothSides()) fleet.inflateIfNeeded();

                battle.genCombined();

                enemyStrength = tallyShipStrength(battle.getNonPlayerCombined().getFleetData().getMembersListCopy(), false, true);
            }


        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    public static double tallyShipStrength(Collection<FleetMemberAPI> fleet, boolean isPlayerSide, boolean log) {
        float fpTotal = 0;
        float fpPerOfficerLevel = isPlayerSide
                ? ModPlugin.ALLY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL
                : ModPlugin.ENEMY_OFFICER_INCREASE_TO_SHIP_STRENGTH_PER_LEVEL;

        for (FleetMemberAPI ship : fleet) {
            if(ship.isFighterWing() || ship.isCivilian() || !ship.canBeDeployedForCombat() || ship.isMothballed())
                continue;

            float fp = getShipStrength(ship);
            float captainBonus = (ship.getCaptain() == Global.getSector().getPlayerPerson() || ship.getCaptain().isDefault()) ? 0
                    : fp * ship.getCaptain().getStats().getLevel() * fpPerOfficerLevel;

            if(log) log(fp + " + " + captainBonus + " = " + (fp + captainBonus) + " : " + ship.getHullId());

            fpTotal += fp + captainBonus;
        }

        if(isPlayerSide) {
            float playerLevelBonus = fpTotal
                    * Global.getSector().getPlayerPerson().getStats().getLevel()
                    * ModPlugin.PLAYER_INCREASE_TO_FLEET_STRENGTH_PER_LEVEL;

            fpTotal *= ModPlugin.PLAYER_FLEET_STRENGTH_MULT;

            if(log) log("Your total deployed strength: " + fpTotal + " + " + playerLevelBonus + " = " + (fpTotal + playerLevelBonus));
            fpTotal += playerLevelBonus;
        } else if(log) log("Total strength: " + fpTotal);

        return Math.max(1, fpTotal);
    }

    static float getShipStrength(FleetMemberAPI ship) {
        float fp = ship.getFleetPointCost();

        if(ship.getHullSpec().isCivilianNonCarrier()) {
            return 0;
        } if(ship.isStation()) {
            return fp;
        } else if(ship.getHullSpec().hasTag("UNBOARDABLE")) {
            float dModMult = ship.getBaseDeploymentCostSupplies() > 0
                    ? (ship.getDeploymentCostSupplies() / ship.getBaseDeploymentCostSupplies())
                    : 1;

            return fp * Math.max(1, Math.min(2, 1 + (fp - 5f) / 25f)) * dModMult;
        } else{
            return ship.getDeploymentCostSupplies();
        }
    }

    public static float adjustSalvageMult(float vanillaSalvageMult) {
        if(battleInvolvesRemnants) vanillaSalvageMult *= ModPlugin.LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER;

        return vanillaSalvageMult * ModPlugin.LOOTED_SALVAGE_MULTIPLIER;
    }

    public static int adjustCreditsLooted(int vanillaCreditsLooted) {
        return (int)(vanillaCreditsLooted * ModPlugin.LOOTED_CREDITS_MULTIPLIER);
    }
}
