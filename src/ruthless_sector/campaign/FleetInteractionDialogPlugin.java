package ruthless_sector.campaign;

import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import ruthless_sector.BattleListener;

public class FleetInteractionDialogPlugin extends FleetInteractionDialogPluginImpl {
    public FleetInteractionDialogPlugin() {
        this(null);
    }

    public FleetInteractionDialogPlugin(FIDConfig params) {
        super(params);

        context = new FleetEncounterContext();
    }

    @Override
    public void backFromEngagement(EngagementResultAPI result) {
        super.backFromEngagement(result);

        if(isFightingOver()) BattleListener.onBattleEnd(textPanel);
    }
}
