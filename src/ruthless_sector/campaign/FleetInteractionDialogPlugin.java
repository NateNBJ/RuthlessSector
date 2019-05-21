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

    @Override
    public void optionSelected(String text, Object optionData) {
        super.optionSelected(text, optionData);

        BattleListener.processSelectedOption(text, optionData);
    }
}
