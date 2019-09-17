package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BattleAutoresolverPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import data.scripts.TiandongModPlugin;
import ruthless_sector.CampaignScript;
import ruthless_sector.ModPlugin;

public class CampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId()
    {
        return "RuthlessSectorCampaignPlugin";
    }

    @Override
    public boolean isTransient() { return true; }

    @Override
    public PluginPick<InteractionDialogPlugin> pickRespawnPlugin() {
        CampaignScript.setPlayerJustRespawned();
        return null;
    }

    @Override
    public PluginPick<BattleAutoresolverPlugin> pickBattleAutoresolverPlugin(BattleAPI battle) {
        try {
            if(battle.isPlayerInvolved()) {
                return new PluginPick<BattleAutoresolverPlugin>(
                        new ruthless_sector.campaign.BattleAutoresolverPlugin(battle),
                        PickPriority.MOD_GENERAL
                );
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return null;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        try {
            if (interactionTarget instanceof CampaignFleetAPI) {
                InteractionDialogPlugin plugin;
                PickPriority priority = PickPriority.MOD_GENERAL;
                ModManagerAPI mm = Global.getSettings().getModManager();

                try {
                    if (mm.isModEnabled("swp") && interactionTarget.getFaction().getId().contentEquals("famous_bounty")) {
                        plugin = new SwpCompatibleFleetInteractionDialogPlugin();
                        priority = PickPriority.HIGHEST; // No other way to ensure compatibility, unfortunately
                    } else if (mm.isModEnabled("nexerelin")) {
                        plugin = new NexCompatibleFleetInteractionDialogPlugin();
                    } else if (mm.isModEnabled("THI") && TiandongModPlugin.useCustomFleetPlugin && anIdleMercFleetIsNearby()) {
                        plugin = new ThiCompatibleFleetInteractionDialogPlugin();
                        priority = PickPriority.MOD_SPECIFIC;
                    } else {
                        plugin = new FleetInteractionDialogPlugin();
                    }
                } catch (Exception e) {
                    plugin = new FleetInteractionDialogPlugin();
                    ModPlugin.reportCrash(e);
                }

                return new PluginPick<>(plugin, priority);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return null;
    }

    boolean anIdleMercFleetIsNearby() {
        for (CampaignFleetAPI mercCandidate : Misc.findNearbyFleets(Global.getSector().getPlayerFleet(), 1500f, null)) {
            if (mercCandidate.getBattle() == null
                    && mercCandidate.getMemoryWithoutUpdate().contains("$tiandongMercTarget")) {

                return true;
            }
        }

        return false;
    }
}
