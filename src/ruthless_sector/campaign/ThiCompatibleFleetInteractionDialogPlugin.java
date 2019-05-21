package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import data.scripts.campaign.TiandongFleetInteractionDialogPlugin;
import ruthless_sector.BattleListener;

public class ThiCompatibleFleetInteractionDialogPlugin extends TiandongFleetInteractionDialogPlugin {
    public ThiCompatibleFleetInteractionDialogPlugin() {
        super();

        Global.getLogger(ThiCompatibleFleetInteractionDialogPlugin.class).info("ThiCompatibleFleetInteractionDialogPlugin chosen");

        context = new FleetEncounterContext();

        BattleListener.setVars(context, this);
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        super.optionSelected(text, optionData);

        BattleListener.processSelectedOption(text, optionData);
    }
}
