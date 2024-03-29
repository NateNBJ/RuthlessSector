package ruthless_sector.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.FleetAdvanceScript;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SDMParams;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SalvageDefenderModificationPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import ruthless_sector.CombatPlugin;
import ruthless_sector.ModPlugin;
import ruthless_sector.campaign.CampaignPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RS_SalvageDefenderInteraction extends BaseCommandPlugin {
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, final Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        final SectorEntityToken entity = dialog.getInteractionTarget();
        final MemoryAPI memory = getEntityMemory(memoryMap);

        final CampaignFleetAPI defenders = memory.getFleet("$defenderFleet");
        if (defenders == null) return false;

        dialog.setInteractionTarget(defenders);

        final FIDConfig config = new FIDConfig();
        config.leaveAlwaysAvailable = true;
        config.showCommLinkOption = false;
        config.showEngageText = false;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        config.showWarningDialogWhenNotHostile = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = true;
        config.impactsEnemyReputation = false;
        config.pullInAllies = false;
        config.pullInEnemies = false;
        config.pullInStations = false;
        config.lootCredits = false;

        config.firstTimeEngageOptionText = "Engage the automated defenses";
        config.afterFirstTimeEngageOptionText = "Re-engage the automated defenses";
        config.noSalvageLeaveOptionText = "Continue";

        config.dismissOnLeave = false;
        config.printXPToDialog = true;

        long seed = memory.getLong(MemFlags.SALVAGE_SEED);
        config.salvageRandom = Misc.getRandom(seed, 75);





        //final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);
        // Line above replaced with below in order to use mod compatible FIDP override
        PluginPick<InteractionDialogPlugin> pick = CampaignPlugin.getEncounterInteractionDialogPlugin(dialog.getInteractionTarget(), config);
        final FleetInteractionDialogPluginImpl plugin = pick.plugin instanceof FleetInteractionDialogPluginImpl
                ? (FleetInteractionDialogPluginImpl)pick.plugin
                : new FleetInteractionDialogPluginImpl(config);

        // Block below added
        {
            String target = null;

            if(ruleId.contains("Probe")) target = "probe";
            else if(ruleId.contains("SurveyShip")) target = "survey ship";
            else if(ruleId.contains("Mothership")) target = "mothership";



            //Global.getLogger(this.getClass()).info(ruleId + "   " + target);

            if((ModPlugin.MAX_ECM_RATING_FOR_AUTOMATED_DEFENSES > 10 || ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES > 1)
                    && target != null) {

                dialog.getTextPanel().addPara("The readings gradually become scrambled as you approach. It seems the " +
                        target +" has activated an unusually effective ECM and targeting support network.");

                CombatPlugin.setDomainDroneEcmBonusFlag();
            }
        }
















        final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
        config.delegate = new BaseFIDDelegate() {
            @Override
            public void notifyLeave(InteractionDialogAPI dialog) {
                // nothing in there we care about keeping; clearing to reduce savefile size
                defenders.getMemoryWithoutUpdate().clear();
                // there's a "standing down" assignment given after a battle is finished that we don't care about
                defenders.clearAssignments();
                defenders.deflate();

                dialog.setPlugin(originalPlugin);
                dialog.setInteractionTarget(entity);

                //Global.getSector().getCampaignUI().clearMessages();

                if (plugin.getContext() instanceof FleetEncounterContext) {
                    FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
                    if (context.didPlayerWinEncounterOutright()) {

                        SDMParams p = new SDMParams();
                        p.entity = entity;
                        p.factionId = defenders.getFaction().getId();

                        SalvageDefenderModificationPlugin plugin = Global.getSector().getGenericPlugins().pickPlugin(
                                SalvageDefenderModificationPlugin.class, p);
                        if (plugin != null) {
                            plugin.reportDefeated(p, entity, defenders);
                        }

                        memory.unset("$hasDefenders");
                        memory.unset("$defenderFleet");
                        memory.set("$defenderFleetDefeated", true);
                        entity.removeScriptsOfClass(FleetAdvanceScript.class);
                        FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
                    } else {
                        boolean persistDefenders = false;
                        if (context.isEngagedInHostilities()) {
                            persistDefenders |= !Misc.getSnapshotMembersLost(defenders).isEmpty();
                            for (FleetMemberAPI member : defenders.getFleetData().getMembersListCopy()) {
                                if (member.getStatus().needsRepairs()) {
                                    persistDefenders = true;
                                    break;
                                }
                            }
                        }

                        if (persistDefenders) {
                            if (!entity.hasScriptOfClass(FleetAdvanceScript.class)) {
                                defenders.setDoNotAdvanceAI(true);
                                defenders.setContainingLocation(entity.getContainingLocation());
                                // somewhere far off where it's not going to be in terrain or whatever
                                defenders.setLocation(1000000, 1000000);
                                entity.addScript(new FleetAdvanceScript(defenders));
                            }
                            memory.expire("$defenderFleet", 10); // defenders may have gotten damaged; persist them for a bit
                        }
                        dialog.dismiss();
                    }
                } else {
                    dialog.dismiss();
                }
            }
            @Override
            public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                bcc.aiRetreatAllowed = false;
                bcc.objectivesAllowed = false;
                bcc.enemyDeployAll = true;
            }
            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                DataForEncounterSide winner = context.getWinnerData();
                DataForEncounterSide loser = context.getLoserData();

                if (winner == null || loser == null) return;

                float playerContribMult = context.computePlayerContribFraction();

                List<DropData> dropRandom = new ArrayList<DropData>();
                List<DropData> dropValue = new ArrayList<DropData>();

                float valueMultFleet = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.BATTLE_SALVAGE_MULT_FLEET);
                float valueModShips = context.getSalvageValueModPlayerShips();

                for (FleetMemberData data : winner.getEnemyCasualties()) {
                    // add at least one of each weapon that was present on the OMEGA ships, since these
                    // are hard to get; don't want them to be too RNG
                    if (data.getMember() != null && context.getBattle() != null) {
                        CampaignFleetAPI fleet = context.getBattle().getSourceFleet(data.getMember());

                        if (fleet != null && fleet.getFaction().getId().equals(Factions.OMEGA)) {
                            for (String slotId : data.getMember().getVariant().getNonBuiltInWeaponSlots()) {
                                String weaponId = data.getMember().getVariant().getWeaponId(slotId);
                                if (weaponId == null) continue;
                                if (salvage.getNumWeapons(weaponId) <= 0) {
                                    WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
                                    if (spec.hasTag(Tags.NO_DROP)) continue;

                                    salvage.addWeapons(weaponId, 1);
                                }
                            }
                        }

                        if (fleet != null &&
                                fleet.getFaction().getCustomBoolean(Factions.CUSTOM_NO_AI_CORES_FROM_AUTOMATED_DEFENSES)) {
                            continue;
                        }
                    }
                    if (config.salvageRandom.nextFloat() < playerContribMult) {
                        DropData drop = new DropData();
                        drop.chances = 1;
                        drop.value = -1;
                        switch (data.getMember().getHullSpec().getHullSize()) {
                            case CAPITAL_SHIP:
                                drop.group = Drops.AI_CORES3;
                                drop.chances = 2;
                                break;
                            case CRUISER:
                                drop.group = Drops.AI_CORES3;
                                break;
                            case DESTROYER:
                                drop.group = Drops.AI_CORES2;
                                break;
                            case FIGHTER:
                            case FRIGATE:
                                drop.group = Drops.AI_CORES1;
                                break;
                        }
                        if (drop.group != null) {
                            dropRandom.add(drop);
                        }
                    }
                }

                float fuelMult = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET);
                //float fuel = salvage.getFuel();
                //salvage.addFuel((int) Math.round(fuel * fuelMult));

                CargoAPI extra = SalvageEntity.generateSalvage(config.salvageRandom, valueMultFleet + valueModShips, 1f, 1f, fuelMult, dropValue, dropRandom);
                for (CargoStackAPI stack : extra.getStacksCopy()) {
                    if (stack.isFuelStack()) {
                        stack.setSize((int)(stack.getSize() * fuelMult));
                    }
                    salvage.addFromStack(stack);
                }

            }

        };


        dialog.setPlugin(plugin);
        plugin.init(dialog);

        return true;
    }
}