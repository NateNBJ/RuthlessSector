package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import ruthless_sector.BattleListener;

public class FleetEncounterContext extends com.fs.starfarer.api.impl.campaign.FleetEncounterContext {
    @Override
    public void setBattle(BattleAPI battle) {
        super.setBattle(battle);
        BattleListener.setBattle(battle);
    }

    @Override
    public void processEngagementResults(EngagementResultAPI result) {
        super.processEngagementResults(result);
        BattleListener.processEngagementResults(result);
    }

    @Override
    public int getCreditsLooted() {
        return BattleListener.adjustCreditsLooted(super.getCreditsLooted());
    }

    @Override
    public float getSalvageMult(Status status) {
        return BattleListener.adjustSalvageMult(super.getSalvageMult(status));
    }

    @Override
    protected void gainXP(DataForEncounterSide side, DataForEncounterSide otherSide) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        float xp = BattleListener.getXpGain(this, side, otherSide);

        if (xp > 0) {
            gainOfficerXP(side, xp);

            fleet.getCommander().getStats().addXP((long) xp, textPanelForXPGain);
            fleet.getCommander().getStats().levelUpIfNeeded(textPanelForXPGain);
        }
    }
}
