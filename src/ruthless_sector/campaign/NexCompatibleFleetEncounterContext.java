package ruthless_sector.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import ruthless_sector.BattleListener;
import ruthless_sector.ModPlugin;

public class NexCompatibleFleetEncounterContext extends exerelin.campaign.battle.NexFleetEncounterContext  {
    @Override
    public void setBattle(BattleAPI battle) {
        super.setBattle(battle);
        BattleListener.setBattle(battle);
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

            xpGained = xp;
        }
    }

    @Override
    public float computeBattleDifficulty() {
        computedDifficulty = true;

        return difficulty = ModPlugin.DISABLE_VANILLA_DIFFICULTY_BONUS ? 0 : super.computeBattleDifficulty();
    }
}