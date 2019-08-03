package ruthless_sector.campaign;

import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import ruthless_sector.BattleListener;

public class FleetInteractionDialogPlugin extends FleetInteractionDialogPluginImpl {
    public FleetInteractionDialogPlugin() {
        this(null);
    }

    public FleetInteractionDialogPlugin(FIDConfig params) {
        super(params);

        context = new FleetEncounterContext();

        BattleListener.setVars(context, this);
    }
}
