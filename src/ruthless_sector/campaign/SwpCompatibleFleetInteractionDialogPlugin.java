package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import ruthless_sector.BattleListener;

public class SwpCompatibleFleetInteractionDialogPlugin extends FleetInteractionDialogPluginImpl {
    public SwpCompatibleFleetInteractionDialogPlugin() {
        this(null);
    }

    public SwpCompatibleFleetInteractionDialogPlugin(FIDConfig params) {
        super(params);

        Global.getLogger(SwpCompatibleFleetInteractionDialogPlugin.class).info("SwpCompatibleFleetInteractionDialogPlugin chosen");

        context = new SwpCompatibleFeetEncounterContext();

        BattleListener.setVars(context, this);
    }
}
