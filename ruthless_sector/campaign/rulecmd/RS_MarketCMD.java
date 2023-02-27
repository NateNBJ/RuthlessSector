package ruthless_sector.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import ruthless_sector.campaign.FleetInteractionDialogPlugin;

import java.util.List;
import java.util.Map;

public class RS_MarketCMD extends MarketCMD {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        return Global.getSettings().getModManager().isModEnabled("nexerelin")
                ? new RS_Nex_MarketCMD().execute(ruleId, dialog, params, memoryMap)
                : super.execute(ruleId, dialog, params, memoryMap);
    }

    // TODO - Between major versions of Starsector, re-copy this method and replace the
    //  FleetInteractionDialogPluginImpl with FleetInteractionDialogPlugin
    @Override
    protected void engage() {
        final SectorEntityToken entity = dialog.getInteractionTarget();
        final MemoryAPI memory = getEntityMemory(memoryMap);

        final CampaignFleetAPI primary = getInteractionTargetForFIDPI();

        dialog.setInteractionTarget(primary);

        final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
        config.leaveAlwaysAvailable = true;
        config.showCommLinkOption = false;
        config.showEngageText = false;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        //config.showWarningDialogWhenNotHostile = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = true;
//		config.impactsEnemyReputation = false;
//		config.pullInAllies = false;
//		config.pullInEnemies = false;
//		config.lootCredits = false;

//		config.firstTimeEngageOptionText = "Engage the automated defenses";
//		config.afterFirstTimeEngageOptionText = "Re-engage the automated defenses";
        config.noSalvageLeaveOptionText = "Continue";

        config.dismissOnLeave = false;
        config.printXPToDialog = true;

        config.straightToEngage = true;

        CampaignFleetAPI station = getStationFleet();
        config.playerAttackingStation = station != null;

        final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPlugin(config);

        final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
        config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
            @Override
            public void notifyLeave(InteractionDialogAPI dialog) {
                if (primary.isStationMode()) {
                    primary.getMemoryWithoutUpdate().clear();
                    primary.clearAssignments();
                    //primary.deflate();
                }

                dialog.setPlugin(originalPlugin);
                dialog.setInteractionTarget(entity);

                boolean quickExit = entity.hasTag(Tags.NON_CLICKABLE);

                if (!Global.getSector().getPlayerFleet().isValidPlayerFleet() || quickExit) {
                    dialog.getOptionPanel().clearOptions();
                    dialog.getOptionPanel().addOption("Leave", "marketLeave");
                    dialog.getOptionPanel().setShortcut("marketLeave", Keyboard.KEY_ESCAPE, false, false, false, true);

                    dialog.showTextPanel();
                    dialog.setPromptText("You decide to...");
                    dialog.getVisualPanel().finishFadeFast();
                    text.updateSize();

//					dialog.hideVisualPanel();
//					dialog.getVisualPanel().finishFadeFast();
//					dialog.hideTextPanel();
//					dialog.dismiss();
                    return;
                }

                if (plugin.getContext() instanceof FleetEncounterContext) {
                    FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
                    if (context.didPlayerWinMostRecentBattleOfEncounter()) {
                        // may need to do something here re: station being defeated & timed out
                        //FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
                    } else {
                        //dialog.dismiss();
                    }

                    if (context.isEngagedInHostilities()) {
                        dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
                    }

                    showDefenses(context.isEngagedInHostilities());
                } else {
                    showDefenses(false);
                }
                dialog.getVisualPanel().finishFadeFast();

                //dialog.dismiss();
            }
            @Override
            public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                //bcc.aiRetreatAllowed = false;
                bcc.objectivesAllowed = false;
            }
            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
            }

        };

        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }
}
