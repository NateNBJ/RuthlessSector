package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;
import data.scripts.TiandongModPlugin;
import org.magiclib.bounty.ActiveBounty;
import org.magiclib.bounty.MagicBountyCoordinator;
import ruthless_sector.CampaignScript;
import ruthless_sector.ModPlugin;

import java.util.Collection;
import java.util.Iterator;

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

    public static PluginPick<InteractionDialogPlugin> getEncounterInteractionDialogPlugin(SectorEntityToken interactionTarget, FleetInteractionDialogPluginImpl.FIDConfig params) {
        InteractionDialogPlugin plugin;
        PickPriority priority = PickPriority.MOD_GENERAL;
        ModManagerAPI mm = Global.getSettings().getModManager();

        try {
            if (mm.isModEnabled("swp") && interactionTarget != null
                    && interactionTarget.getFaction().getId().contentEquals("famous_bounty")) {

                plugin = new SwpCompatibleFleetInteractionDialogPlugin(params);
                priority = PickPriority.HIGHEST; // No other way to ensure compatibility, unfortunately
            } else if (mm.isModEnabled("MagicLib") && isTargetMagicBounty(interactionTarget)) {
                plugin = new MagicBountyCompatibleInteractionDialogPlugin(params);
                priority = PickPriority.MOD_SPECIFIC;
            } else if (mm.isModEnabled("nexerelin")) {
                plugin = new NexCompatibleFleetInteractionDialogPlugin(params);
                priority = PickPriority.MOD_GENERAL;
            } else if (mm.isModEnabled("THI") && TiandongModPlugin.useCustomFleetPlugin && anIdleMercFleetIsNearby()) {
                plugin = new ThiCompatibleFleetInteractionDialogPlugin();
                priority = PickPriority.MOD_SPECIFIC;
            } else {
                plugin = new FleetInteractionDialogPlugin(params);
            }
        } catch (Exception e) {
            plugin = new FleetInteractionDialogPlugin(params);
            ModPlugin.reportCrash(e);
        }

        return new PluginPick<>(plugin, priority);
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        try {
            if (interactionTarget instanceof CampaignFleetAPI) {
                return getEncounterInteractionDialogPlugin(interactionTarget, null);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return null;
    }

    public static boolean anIdleMercFleetIsNearby() {
        for (CampaignFleetAPI mercCandidate : Misc.findNearbyFleets(Global.getSector().getPlayerFleet(), 1500f, null)) {
            if (mercCandidate.getBattle() == null
                    && mercCandidate.getMemoryWithoutUpdate().contains("$tiandongMercTarget")) {

                return true;
            }
        }

        return false;
    }

    public static boolean isTargetMagicBounty(SectorEntityToken interactionTarget) {
        Collection<ActiveBounty> bounties = MagicBountyCoordinator.getInstance().getActiveBounties().values();
        if (bounties.size() > 0 && interactionTarget instanceof CampaignFleetAPI) {
            Iterator i$ = bounties.iterator();

            while(i$.hasNext()) {
                ActiveBounty bounty = (ActiveBounty)i$.next();
                if (bounty.getFlagshipId() != null && bounty.getFlagshipId().equals(((CampaignFleetAPI)interactionTarget).getFlagship().getId())) {
                    return true;
                }
            }
        }

        return false;
    }
}
