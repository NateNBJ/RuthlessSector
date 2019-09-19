package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.*;
import java.util.List;

public class StrengthTrackingCombatPlugin implements EveryFrameCombatPlugin {
    static final float
            SHIP_LIST_TOP = 51,
            SHIP_LIST_LEFT = -298,
            SHIP_GRID_SIZE = 58,
            PIXEL_WIDTH = 1 / (Global.getSettings().getScreenWidth() * 0.5f),
            PIXEL_HEIGHT = 1 / (Global.getSettings().getScreenHeight() * 0.5f);
    static final int
            ESCAPE_KEY_VALUE = 1,
            DEFAULT_SHIP_LIMIT = 30;
    static final Color clr50 = new Color(155, 255, 0),
            clr75 = new Color(205, 233, 0),
            clr100 = new Color(255, 210, 0),
            clr125 = new Color(255, 155, 0),
            clr150 = new Color(255, 100, 0);

    SpriteAPI sprite;
    CombatFleetManager pf, ef;
    Set<FleetMemberAPI> selectedForDeployment = new HashSet<>();
    Map<FleetMemberAPI, Integer> clickCount = new HashMap<>();
    CombatEngineAPI engine;


    float deployedStrength = Float.MIN_VALUE, selectedStrength = 0, deployedDP = Float.MIN_VALUE, selectedDP = 0;
    float timeShowingDeployMenu = 0;
    boolean escapeMenuIsOpen = false, mouseDownOverAllBtn = false, playerIsPursuing = false;
    int mouseX, mouseY, limit;

    boolean isIrrelevant() { return !ModPlugin.SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN || engine == null
            || !engine.isInCampaign() || engine.isSimulation() || engine.isInCampaignSim(); }


    @Override
    public void init(CombatEngineAPI engine) {
        try {
            this.engine = engine;

            if (isIrrelevant()) return;

            deployedDP = deployedStrength = Float.MIN_VALUE;
            selectedDP = selectedStrength = 0;
            limit = engine.getFleetManager(FleetSide.PLAYER).getMaxStrength();

            playerIsPursuing = engine.getContext().getOtherGoal() == FleetGoal.ESCAPE;
            ef = CombatEngine.getInstance().getFleetManager(1);
            pf = CombatEngine.getInstance().getFleetManager(0);
            sprite = Global.getSettings().getSprite("ui", "icon_fleet_danger");
            sprite.setWidth(sprite.getWidth() * PIXEL_WIDTH);
            sprite.setHeight(sprite.getHeight() * PIXEL_HEIGHT);

            if (CampaignScript.timeHasPassedSinceLastEngagement) { // Then this is the start of a new battle
                CampaignScript.timeHasPassedSinceLastEngagement = false;
                ModPlugin.battleStartedSinceLastSave = true;
                float efStrength = 0;
                for (FleetMemberAPI fm : ef.getReservesCopy()) efStrength += ModPlugin.getShipStrength(fm);
                for (FleetMemberAPI fm : ef.getDeployedCopy()) efStrength += ModPlugin.getShipStrength(fm);

                ModPlugin.resetIntegrationValues();
                ModPlugin.updateEnemyStrength(efStrength);
                if(ModPlugin.battlesResolvedSinceLastSave == 0) ModPlugin.adjustReloadPenalty(ModPlugin.RELOAD_PENALTY_PER_RELOAD);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void advance(float amount, java.util.List<InputEventAPI> events) {
        try {
            if (isIrrelevant()) return;

            if(engine.isUIShowingDialog() && !escapeMenuIsOpen && pf.getReservesCopy().size() <= DEFAULT_SHIP_LIMIT) {
                if(deployedStrength == Float.MIN_VALUE) {
                    deployedDP = deployedStrength = 0;
                    limit = engine.getFleetManager(FleetSide.PLAYER).getMaxStrength();

                    Set<FleetMemberAPI> deployedShips = new HashSet();

                    deployedShips.addAll(pf.getDeployedCopy());
                    deployedShips.addAll(pf.getRetreatedCopy());
                    deployedShips.addAll(pf.getDisabledCopy());
                    deployedShips.addAll(pf.getDestroyedCopy());

                    for(FleetMemberAPI ship : deployedShips) deployedStrength += ModPlugin.getShipStrength(ship);

                    for(FleetMemberAPI ship : pf.getDeployedCopy()) deployedDP += ship.getDeploymentPointsCost();
                }

                render();

                timeShowingDeployMenu += engine.getElapsedInLastFrame();
            } else {
                deployedDP = deployedStrength = Float.MIN_VALUE;
                selectedDP = selectedStrength = 0;
                timeShowingDeployMenu = 0;
                selectedForDeployment.clear();
                clickCount.clear();
            }

        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    FleetMemberAPI getShipUnderCursor() {
        if(pf.getReservesCopy().size() > DEFAULT_SHIP_LIMIT) return null;

        int row = (int)Math.floor((-mouseY + SHIP_LIST_TOP) / SHIP_GRID_SIZE);
        int column = (int)Math.floor((mouseX - SHIP_LIST_LEFT) / SHIP_GRID_SIZE);

        switch (mouseX) {
            case -241: column = 1; break;
            case -124: column = 3; break;
            case  -67: column = 4; break;
            case  107: column = 7; break;
            case  281: column = 10; break;
        }


        int i = row * 10 + column;

        return (i >= 0 && i < pf.getReservesCopy().size() && column >= 0 && column <= 9)
                ? pf.getReservesCopy().get(i) : null;
    }

    boolean mouseIsOverAllButton() {
        final int LEFT = 104, RIGHT = 194, BOTTOM = -193, TOP = -166;

        return engine.isUIShowingDialog() && !escapeMenuIsOpen && mouseX >= LEFT && mouseX <= RIGHT
                && mouseY >= BOTTOM && mouseY <= TOP;
    }

    public void render() {
        double pfStrength = Math.max(deployedStrength + selectedStrength, ModPlugin.playerStrength.val);
        int danger = CampaignScript.getDangerStars(pfStrength, ModPlugin.enemyStrength.val);
        float xSpacing = sprite.getWidth() + PIXEL_WIDTH * 2,
                y = (Global.getSettings().getScreenHeight() * 0.5f - 10) * PIXEL_HEIGHT - sprite.getHeight();

        switch (danger) {
            case 1: sprite.setColor(clr50); break;
            case 2: sprite.setColor(clr75); break;
            case 3: sprite.setColor(clr100); break;
            case 4: sprite.setColor(clr125); break;
            default: sprite.setColor(clr150); break;
        }

        for(int i = 0; i < danger; ++i) {
            float x = i * xSpacing - danger * xSpacing * 0.5f;

            sprite.render(x, y);
        }

        //drawDebugShip();
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        try {
            if (isIrrelevant()) return;

            if (!engine.isUIShowingDialog()) escapeMenuIsOpen = false;

            mouseX = (int) (Mouse.getX() - Global.getSettings().getScreenWidth() * 0.5f);
            mouseY = (int) (Mouse.getY() - Global.getSettings().getScreenHeight() * 0.5f);

            for (InputEventAPI e : events) {
                if (e.isConsumed()) continue;
//            if(e.isMouseDownEvent()) {
//                Global.getLogger(this.getClass()).info("DOWN - X: " + mouseX + "  Y: " + mouseY + "  Button: " + e.getEventValue());
//            } else if(e.isMouseUpEvent()) {
//                Global.getLogger(this.getClass()).info("UP - X: " + mouseX + "  Y: " + mouseY + "  Button: " + e.getEventValue());
//            }

                if (e.isMouseDownEvent()) {
                    FleetMemberAPI fm = getShipUnderCursor();

                    if(fm != null) {
                        float newDP = deployedDP + selectedDP + fm.getDeploymentPointsCost();

                        if(!selectedForDeployment.contains(fm) && newDP > limit) continue;

                        if(!clickCount.containsKey(fm)) clickCount.put(fm, 1);
                        else clickCount.put(fm, clickCount.get(fm) + 1);

                        boolean toggle = !fm.isFrigate() || !playerIsPursuing || clickCount.get(fm) % 4 < 2;

                        if(toggle) {
                            if (selectedForDeployment.contains(fm)) {
                                selectedDP -= fm.getDeploymentPointsCost();
                                selectedStrength -= ModPlugin.getShipStrength(fm);
                                selectedForDeployment.remove(fm);
                            } else {
                                selectedDP += fm.getDeploymentPointsCost();
                                selectedStrength += ModPlugin.getShipStrength(fm);
                                selectedForDeployment.add(fm);
                            }
                        }

                    } else if (mouseIsOverAllButton() && e.getEventValue() == 0) {
                        mouseDownOverAllBtn = true;
                    }
                } else if (e.isKeyDownEvent() && e.getEventValue() == ESCAPE_KEY_VALUE) {
                    escapeMenuIsOpen = true;
                }
            }

            if (!Mouse.isButtonDown(0)) { // Mouse up event is consumed at the end of a button press
                if (mouseDownOverAllBtn && mouseIsOverAllButton()) {
                    if (selectedForDeployment.size() == pf.getReservesCopy().size()) {
                        selectedForDeployment.clear();
                        clickCount.clear();
                        selectedDP = selectedStrength = 0;
                    } else {
                        boolean noCombatShipsAdded = true;

                        for (FleetMemberAPI ship : pf.getReservesCopy()) {
                            int newDP = (int)(deployedDP + selectedDP + ship.getDeploymentPointsCost());

                            if (!selectedForDeployment.contains(ship) && !ship.isCivilian()
                                    && ship.canBeDeployedForCombat() && !ship.isMothballed()
                                    && newDP <= limit) {

                                noCombatShipsAdded = false;
                                selectedDP += ship.getDeploymentPointsCost();
                                selectedStrength += ModPlugin.getShipStrength(ship);
                                selectedForDeployment.add(ship);
                            }
                        }

                        if (noCombatShipsAdded) {
                            for (FleetMemberAPI ship : pf.getReservesCopy()) {
                                int newDP = (int)(deployedDP + selectedDP + ship.getDeploymentPointsCost());

                                if (!selectedForDeployment.contains(ship) && newDP <= limit) {
                                    selectedDP += ship.getDeploymentPointsCost();
                                    selectedStrength += ModPlugin.getShipStrength(ship);
                                    selectedForDeployment.add(ship);
                                }
                            }
                        }
                    }
                }

                mouseDownOverAllBtn = false;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

    @Override
    public void renderInUICoords(ViewportAPI viewport) { }
}
