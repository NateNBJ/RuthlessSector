package ruthless_sector.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.combat.CombatEngine;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import ruthless_sector.ModPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrintStrengthEstimates implements BaseCommand {
    final String LS = System.lineSeparator();
    final String PREAMBLE = "Base estimated strength is derived from the Fleet Points (FP) of the ship hull, which is determined by the ship creator."
            + LS + "Civilian ships are always estimated to have 0 strength, and are not listed.";
    final String HEADER = "FP    SHIP                                               STRENGTH     BASE     D-MODS     S-MODS     CAPTAIN    COMMAND";
    final String FORMAT = "%-5s %-50s %-10s %-10s %-10s %-10s %-10s %s";
    final Map<String, Float> SUMS = new HashMap<>();

    float playerSum = 0, enemySum = 0;

    public CommandResult runCommand(@NotNull String args, @NotNull CommandContext context) {
        try {
            if (!context.isCampaignAccessible() || Global.getSector() == null) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            StringBuilder sb = new StringBuilder(LS).append(PREAMBLE).append(LS).append(LS);
            CombatEngineAPI combatEngine = CombatEngine.getInstance();

            playerSum = 0;
            enemySum = 0;

            sb.append("-----<  YOUR SHIPS  >--------------------------------------------------------------------------------------------------");
            sb.append(LS).append(HEADER);
            for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                appendShipStrength(sb, ship, true);
            }
            sb.append(LS);

            if(combatEngine != null && combatEngine.getFleetManager(1) != null) {
                CombatFleetManagerAPI fm = combatEngine.getFleetManager(1);
                List<FleetMemberAPI> defeated = new ArrayList<>();

//                defeated.addAll(fm.getDestroyedCopy());
                defeated.addAll(fm.getDisabledCopy()); // Disabled includes destroyed, apparently
                defeated.addAll(fm.getRetreatedCopy());

                appendEnemyForces(sb, "DEPLOYED", fm.getDeployedCopy());
                appendEnemyForces(sb, "RESERVED", fm.getReservesCopy());
                appendEnemyForces(sb, "DEFEATED", defeated);
            }

            if(playerSum > 0) sb.append(LS).append("Total estimated strength of your ships: ").append(playerSum);
            if(enemySum > 0) sb.append(LS).append("Total estimated strength of enemy ships: ").append(enemySum);

            Console.showMessage(sb);
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }

        return CommandResult.SUCCESS;
    }

    void appendEnemyForces(StringBuilder sb, String category, List<FleetMemberAPI> ships) {
        if(!ships.isEmpty()) {
            sb.append(LS);
            sb.append("-----<  " + category + " ENEMY SHIPS  >----------------------------------------------------------------------------------------");
            sb.append(LS).append(HEADER);
            for(FleetMemberAPI ship : ships) appendShipStrength(sb, ship, false);
            sb.append(LS);
        }
    }
    void appendShipStrength(StringBuilder sb, FleetMemberAPI ship, boolean isPlayerShip) {
        float fp = ship.getFleetPointCost();
        float base, dModMult = 1, sModMult = 1, skillMult = 1, commandMult = 1;
        float strength = ModPlugin.getShipStrength(ship, isPlayerShip);

        // TODO - if strength estimate algo changes then this also needs to be updated
        if(ship.isFighterWing() || !ship.canBeDeployedForCombat() || ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed()) {
            return;
        } else if(ship.isStation()) {
            ShipVariantAPI variant = ship.getVariant();
            List<String> slots = variant.getModuleSlots();
            float totalOP = 0, detachedOP = 0;

            for(int i = 0; i < slots.size(); ++i) {
                ShipVariantAPI module = variant.getModuleVariant(slots.get(i));
                float op = module.getHullSpec().getOrdnancePoints(null);

                totalOP += op;

                if(ship.getStatus().isPermaDetached(i+1)) {
                    detachedOP += op;
                }
            }

            base = (fp * (totalOP - detachedOP) / Math.max(1, totalOP));
        } else {
            float dModFactor = isPlayerShip ? ModPlugin.DMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.DMOD_FACTOR_FOR_ENEMY_SHIPS;
            float sModFactor = isPlayerShip ? ModPlugin.SMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SMOD_FACTOR_FOR_ENEMY_SHIPS;
            float skillFactor = isPlayerShip ? ModPlugin.SKILL_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SKILL_FACTOR_FOR_ENEMY_SHIPS;

            float dMods = DModManager.getNumDMods(ship.getVariant());
            float sMods = ship.getVariant().getSMods().size();
            float skills = 0;
            PersonAPI captain = ship.getCaptain();

            if(captain != null && !captain.isDefault()) {
                for(MutableCharacterStatsAPI.SkillLevelAPI skill : captain.getStats().getSkillsCopy()) {
                    if (skill.getSkill().isCombatOfficerSkill()) {
                        if(skill.getLevel() > 0) skills += skill.getSkill().isElite() ? 1.25f : 1;
                    }
                }
            }

            base = (fp * (1 + (fp - 5f) / 25f));
            dModMult = (float) Math.pow(1 - dModFactor, dMods);
            sModMult = (float) Math.pow(1 + sModFactor, sMods);
            skillMult = (float) Math.pow(1 + skillFactor, skills);
            commandMult = 1;

            // The effect of SiC on enemy fleet seems to be minor, and power from the player's SiC officers is covered by general power based on player level
//            if(ModPlugin.SIC_IS_ACTIVE) {
//                SCData command = SCUtils.getFleetData(ship.getFleetData().getFleet());
//                List<SCBaseSkillPlugin> commandSkills = command.getAllActiveSkillsPlugins();
//                commandMult += commandSkills.size() * ModPlugin.STRENGTH_INCREASE_PER_PLAYER_LEVEL; // TODO
//            } else

            if(isPlayerShip) {
                MutableCharacterStatsAPI stats = Global.getSector().getPlayerPerson().getStats();
//                int effectiveLevel = Math.max(0, Math.min(15, stats.getLevel() - stats.getPoints()));
                int level = Math.max(0, Math.min(15, stats.getLevel())); // It's best to use actual level, since it is used as a general measure of progress rather than actual power

                commandMult += ModPlugin.STRENGTH_INCREASE_PER_PLAYER_LEVEL * level;
            }
        }

        sb.append(System.lineSeparator()).append(String.format(FORMAT,
                subStr(ship.getFleetPointCost() + "", 5),
                subStr(ship.getHullSpec().getHullName() + " - " + ship.getShipName(), 50),
                subStr(String.format("%.2f", strength), 10),
                subStr(String.format("= %.2f", base), 10),
                subStr(dModMult == 1 ? "" : String.format("x%.2f", dModMult), 10),
                subStr(sModMult == 1 ? "" : String.format("x%.2f", sModMult), 10),
                subStr(skillMult == 1 ? "" : String.format("x%.2f", skillMult), 10),
                subStr(commandMult == 1 ? "" : String.format("x%.2f", commandMult), 10)
        ));

        if(isPlayerShip) playerSum += strength;
        else enemySum += strength;
    }
    String subStr(String str, int length) {
        return str == null ? "-" : str.substring(0, Math.min(length, str.length()));
    }
}
