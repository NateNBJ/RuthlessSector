package ruthless_sector.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.battle.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;
import ruthless_sector.campaign.NexCompatibleFleetInteractionDialogPlugin;

public class RS_Nex_MarketCMD extends Nex_MarketCMD {
//    public RS_Nex_MarketCMD(SectorEntityToken entity) {
//        super(entity);
//    }

    // TODO - Between major versions of Nex, re-copy this method and replace the
    //  NexFleetInteractionDialogPluginImpl with NexCompatibleFleetInteractionDialogPlugin
    //  Up to date as of Nex v0.11.1
    @Override
    protected void engage() {
        final SectorEntityToken entity = dialog.getInteractionTarget();
        final MemoryAPI memory = getEntityMemory(memoryMap);
        final MemoryAPI memoryMarket = memoryMap.get(MemKeys.MARKET);

        final CampaignFleetAPI primary = getInteractionTargetForFIDPI();

        dialog.setInteractionTarget(primary);

        final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
        config.leaveAlwaysAvailable = true;
        config.showCommLinkOption = false;
        config.showEngageText = false;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = true;
        config.noSalvageLeaveOptionText = StringHelper.getString("continue", true);

        config.dismissOnLeave = false;
        config.printXPToDialog = true;

        config.straightToEngage = true;

        CampaignFleetAPI station = getStationFleet();
        config.playerAttackingStation = station != null;

        final NexFleetInteractionDialogPluginImpl plugin = new NexCompatibleFleetInteractionDialogPlugin(config);

        final TextPanelAPI text2 = text;	// needed to prevent an IllegalAccessError

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
                    dialog.getOptionPanel().addOption(StringHelper.getString("leave", true), "marketLeave");
                    dialog.getOptionPanel().setShortcut("marketLeave", Keyboard.KEY_ESCAPE, false, false, false, true);

                    dialog.showTextPanel();
                    dialog.setPromptText("You decide to...");
                    dialog.getVisualPanel().finishFadeFast();
                    try {
                        text2.updateSize();
                    } catch (Error ex) {
                        log.error("Text panel error", ex);
                    }

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

                    // MODIFIED: Response fleet handling
                    // Adapted from SalvageDefenderInteraction
                    CampaignFleetAPI responder = memoryMarket.getFleet(ResponseFleetManager.MEMORY_KEY_FLEET);

                    if (context.didPlayerWinEncounterOutright()) {
                        memoryMarket.set(ResponseFleetManager.MEMORY_KEY_FLEET, null, ResponseFleetManager.RESPONSE_FLEET_TTL);
                        if (responder != null) responder.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
                    } else if (responder != null) {
                        //log.info("Running responder cleanup check");
                        boolean persistResponders = false;
                        if (context.isEngagedInHostilities()) {
                            persistResponders |= !Misc.getSnapshotMembersLost(responder).isEmpty();
                            for (FleetMemberAPI member : responder.getFleetData().getMembersListCopy()) {
                                if (member.getStatus().needsRepairs()) {
                                    persistResponders = true;
                                    break;
                                }
                            }
                        }
                        if (persistResponders) {
                            // push the fleet out into the real world, easier than trying to babysit it
                            responder.getMemoryWithoutUpdate().set("$nex_responder_no_cleanup", true);
                            responder.addAssignment(FleetAssignment.ORBIT_PASSIVE, entity, ResponseFleetManager.RESPONSE_FLEET_TTL);
                            responder.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, entity, 999);
                            memoryMarket.set(ResponseFleetManager.MEMORY_KEY_FLEET, null, ResponseFleetManager.RESPONSE_FLEET_TTL);
                        } else {
                            cleanupResponder(responder);
                        }
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
