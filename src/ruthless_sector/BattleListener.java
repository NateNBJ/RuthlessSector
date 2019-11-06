package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class BattleListener {
    static void log(String message) { if(true) Global.getLogger(BattleListener.class).info(message); }

    static boolean battleInvolvesRemnants = false;

    public static void setBattle(BattleAPI battle) {
        try {
            battleInvolvesRemnants = false;

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

            if (xp > 0 || ModPlugin.getReloadPenalty() >= 1) {
                float rsXp = xp * ModPlugin.battleDifficulty.val.floatValue();
                float modifier = 100f * (rsXp / xp);

                if(ModPlugin.SCALE_XP_GAIN_BASED_ON_BATTLE_DIFFICULTY) {
                    xp = rsXp;

                    if (ModPlugin.SHOW_DIFFICULTY_MULTIPLIER_NOTIFICATION) {
                        MessageIntel intel = new MessageIntel();
                        String highlightStr = (int)modifier + "%";

                        if((float)ModPlugin.getReloadPenalty() > 0) {
                            highlightStr += "  (reduced by "
                                    + Misc.getRoundedValue((float)ModPlugin.getReloadPenalty() * 100)
                                    + "% due to reloading after battle)";
                        }

                        intel.addLine("Battle Difficulty: %s", Misc.getTextColor(),
                                new String[] { highlightStr }, Misc.getHighlightColor() );

                        Global.getSector().getCampaignUI().addMessage(intel);
                    }
                }

                if(ModPlugin.GAIN_REPUTATION_FOR_IMPRESSIVE_VICTORIES && modifier > 100 && context.didPlayerWinEncounter()) {
                    float repGain = (modifier / 100f - 1f) * 10 * (float)(Math.pow(ModPlugin.enemyStrength.val / 50f, 0.3f) - 0.3f);
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
}
