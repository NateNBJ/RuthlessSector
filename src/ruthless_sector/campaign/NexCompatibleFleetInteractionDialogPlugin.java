package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import exerelin.campaign.battle.NexFleetInteractionDialogPluginImpl;
import ruthless_sector.BattleListener;

public class NexCompatibleFleetInteractionDialogPlugin extends NexFleetInteractionDialogPluginImpl {
    public NexCompatibleFleetInteractionDialogPlugin() {
        this(null);
    }

    public NexCompatibleFleetInteractionDialogPlugin(FleetInteractionDialogPluginImpl.FIDConfig params) {
        super(params);

        Global.getLogger(NexCompatibleFleetInteractionDialogPlugin.class).info("NexCompatibleFleetInteractionDialogPlugin chosen");

        context = new NexCompatibleFeetEncounterContext();
    }
}
