package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class BattleListener {
    static void log(String message) { if(true) Global.getLogger(BattleListener.class).info(message); }

    static boolean
            battleInvolvesRemnants = false,
            battleWasAutoresolved = true;

    public static void setBattle(BattleAPI battle) {
        try {
            battleInvolvesRemnants = false;
            battleWasAutoresolved = true;

            if(battle == null || battle.getBothSides() == null) return;

            for (CampaignFleetAPI fleet : battle.getBothSides()) {
                if(fleet.getFaction() == null) continue;

                if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
                    battleInvolvesRemnants = true;
                }
            }
        } catch(Exception e) { ModPlugin.reportCrash(e); }
    }

    public static float getXpGain(com.fs.starfarer.api.impl.campaign.FleetEncounterContext context, FleetEncounterContextPlugin.DataForEncounterSide side, FleetEncounterContextPlugin.DataForEncounterSide otherSide) {
        try {
            float bonusXP = 0f;
            float points = 0f;
            for (FleetEncounterContextPlugin.FleetMemberData data : side.getOwnCasualties()) {
                if (data.getStatus() == FleetEncounterContextPlugin.Status.DISABLED ||
                        data.getStatus() == FleetEncounterContextPlugin.Status.DESTROYED) {
                    float [] bonus = Misc.getBonusXPForScuttling(data.getMember());
                    points += bonus[0];
                    bonusXP += bonus[1];
                }
            }
            if (bonusXP > 0 && points > 0) {
                Global.getSector().getPlayerStats().setOnlyAddBonusXPDoNotSpendStoryPoints(true);
                Global.getSector().getPlayerStats().spendStoryPoints((int)Math.round(points), true, context.textPanelForXPGain, false, bonusXP, null);
                Global.getSector().getPlayerStats().setOnlyAddBonusXPDoNotSpendStoryPoints(false);
            }

            //CampaignFleetAPI fleet = side.getFleet();
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            int fpTotal = 0;
            for (FleetEncounterContextPlugin.FleetMemberData data : otherSide.getOwnCasualties()) {
                float fp = data.getMember().getFleetPointCost();
                fp *= 1f + data.getMember().getCaptain().getStats().getLevel() / 5f;
                fpTotal += fp;
            }

            float xp = (float) fpTotal * 250;
            xp *= 2f;

            float difficultyMult = Math.max(1f, context.getDifficulty());
            xp *= difficultyMult;

            xp *= context.computePlayerContribFraction();

            xp *= Global.getSettings().getFloat("xpGainMult");

            // Vanilla code ends, RS code begins

            if (xp > 0) {
                float difficulty = (float)ModPlugin.getDifficultyForLastBattle();
                float rsXp = (float)(xp * ModPlugin.getXpMultiplierForLastBattle());

                if(ModPlugin.SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY) {
                    xp = battleWasAutoresolved ? 0 : rsXp;
                }

                if(ModPlugin.GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES
                        && difficulty > 1
                        && context.didPlayerWinEncounterOutright()
                        && otherSide.getFleet().getFaction().isShowInIntelTab()
                        && !battleWasAutoresolved) {

                    double opposition = Math.min(ModPlugin.enemyStrength.val, 400);
                    float repGain = (difficulty - 1f) * 10 * (float)(Math.pow(opposition / 100f, 0.3f) - 0.3f);
                    WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker();

                    for(FactionAPI faction : ModPlugin.getAllowedFactions()) {
                        if(faction != otherSide.getFleet().getFaction()
                                && faction.getRelationship(otherSide.getFleet().getFaction().getId()) <= -0.5f // -0.5 is hostile threshhold
                                && faction.getRelToPlayer().getLevel() != RepLevel.VENGEFUL
                                && faction.getRelToPlayer().getLevel() != RepLevel.HOSTILE
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

            return xp;
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return 0;
    }

    public static float adjustSalvageMult(float vanillaSalvageMult) {
        if(battleInvolvesRemnants) vanillaSalvageMult *= ModPlugin.LOOTED_SALVAGE_FROM_REMNANTS_MULTIPLIER;

        return vanillaSalvageMult * ModPlugin.LOOTED_SALVAGE_MULTIPLIER;
    }

    public static int adjustCreditsLooted(int vanillaCreditsLooted) {
        return (int)(vanillaCreditsLooted * ModPlugin.LOOTED_CREDITS_MULTIPLIER);
    }

    public static void onBattleEnd(TextPanelAPI textPanel) {
        try {
            if (ModPlugin.SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION && !battleWasAutoresolved) {
                final double difficulty = ModPlugin.getXpMultiplierForLastBattle();
                final double penalty = ModPlugin.getReloadPenalty();
                final String format = "XP multiplier based on battle difficulty: %s"
                        + (penalty > 0 ? "\n(reduced by %s due to penalty for reloading after battle)" : "");

                TooltipMakerAPI tooltip = textPanel.beginTooltip();
                tooltip.addPara(format, 0, Misc.getGrayColor(), Misc.getHighlightColor(),
                        (int) (100 * difficulty) + "%", (int) (100 * penalty) + "%");
                textPanel.addTooltip();
            }
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }
}
