package ruthless_sector.campaign;

import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.BattleAutoresolverPluginImpl;

public class BattleAutoresolverPlugin extends BattleAutoresolverPluginImpl {

    public BattleAutoresolverPlugin(BattleAPI battle) {
        super(battle);
    }
    
    @Override
    public void resolve() {
            // figure out battle type (escape vs engagement)

            report("***");
            report("***");
            report(String.format("Autoresolving %s vs %s", one.getNameWithFaction(), two.getNameWithFaction()));

            context = new FleetEncounterContext();
            context.setAutoresolve(true);
            context.setBattle(battle);
            CampaignFleetAIAPI.EncounterOption optionOne = one.getAI().pickEncounterOption(context, two);
            CampaignFleetAIAPI.EncounterOption optionTwo = two.getAI().pickEncounterOption(context, one);

            if (optionOne == CampaignFleetAIAPI.EncounterOption.DISENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.DISENGAGE) {
                    report("Both fleets want to disengage");
                    report("Finished autoresolving engagement");
                    report("***");
                    report("***");
                    return;
            }

            boolean oneEscaping = false;
            boolean twoEscaping = false;

            boolean freeDisengageIfCanOutrun = false;

            if (optionOne == CampaignFleetAIAPI.EncounterOption.DISENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.ENGAGE) {
                    report(String.format("%s wants to disengage", one.getNameWithFaction()));
                    oneEscaping = true;
                    if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(one, two)) {
                            report(String.format("%s can outrun other fleet", one.getNameWithFaction()));
                            report("Finished autoresolving engagement");
                            report("***");
                            report("***");
                            return;
                    }
            }
            if (optionOne == CampaignFleetAIAPI.EncounterOption.ENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.DISENGAGE) {
                    report(String.format("%s wants to disengage", two.getNameWithFaction()));
                    twoEscaping = true;
                    if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(two, one)) {
                            report(String.format("%s can outrun other fleet", two.getNameWithFaction()));
                            report("Finished autoresolving engagement");
                            report("***");
                            report("***");
                            return;
                    }
            }

            resolveEngagement(context, oneEscaping, twoEscaping);

            report("");
            report("Finished autoresolving engagement");
            report("***");
            report("***");
    }
}
