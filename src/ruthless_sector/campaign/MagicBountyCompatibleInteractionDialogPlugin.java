package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import org.magiclib.bounty.MagicBountyFleetInteractionDialogPlugin;
import ruthless_sector.BattleListener;

public class MagicBountyCompatibleInteractionDialogPlugin extends MagicBountyFleetInteractionDialogPlugin {
    public MagicBountyCompatibleInteractionDialogPlugin() {
        this(null);
    }

    public MagicBountyCompatibleInteractionDialogPlugin(FleetInteractionDialogPluginImpl.FIDConfig params) {
        super(params);

        Global.getLogger(MagicBountyFleetInteractionDialogPlugin.class).info("MagicBountyFleetInteractionDialogPlugin chosen");

        context = new MagicBountyCompatibleFleetEncounterContext();
    }

    @Override
    public void backFromEngagement(EngagementResultAPI result) {
        super.backFromEngagement(result);

        if(isFightingOver()) BattleListener.onBattleEnd(textPanel);
    }
}
