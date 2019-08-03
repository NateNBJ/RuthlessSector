package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class StrengthTrackingCombatPlugin implements EveryFrameCombatPlugin {
    static final float
            HEIGHT_SPACE = 15f,
            BAR_WIDTH = 279f,
            Y_POS = -179,
            INDICATOR_DELAY = 0.3f,
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
    CombatEngineAPI engine;

    float deployedStrength = Float.MIN_VALUE, selectedStrength = 0;
    float timeShowingDeployMenu = 0;
    boolean escapeMenuIsOpen = false, mouseDownOverAllBtn = false;
    int mouseX, mouseY;

    boolean isIrrelevant() { return !ModPlugin.SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN || engine == null
            || !engine.isInCampaign() || engine.isSimulation() || engine.isInCampaignSim(); }

    @Override
    public void init(CombatEngineAPI engine) {
        try {
            this.engine = engine;

            if (isIrrelevant()) return;


            deployedStrength = Float.MIN_VALUE;
            selectedStrength = 0;

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
                    deployedStrength = 0;

                    Map<FleetMemberAPI, Float> playerDeployedFP = new HashMap();

                    for(FleetMemberAPI fm : pf.getDeployedCopy()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
                    for(FleetMemberAPI fm : pf.getRetreated()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
                    for(FleetMemberAPI fm : pf.getDisabledCopy()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));
                    for(FleetMemberAPI fm : pf.getDestroyed()) playerDeployedFP.put(fm, ModPlugin.getShipStrength(fm));

                    for(Float strength : playerDeployedFP.values()) deployedStrength += strength;
                }

                render();

                timeShowingDeployMenu += engine.getElapsedInLastFrame();
            } else {
                deployedStrength = Float.MIN_VALUE;
                timeShowingDeployMenu = 0;
                selectedStrength = 0;
                selectedForDeployment.clear();
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

                    if (mouseIsOverAllButton() && e.getEventValue() == 0) {
                        mouseDownOverAllBtn = true;
                    } else if (fm != null && selectedForDeployment.contains(fm)) {
                        selectedStrength -= ModPlugin.getShipStrength(fm);
                        selectedForDeployment.remove(fm);
                    } else if (fm != null) {
                        selectedStrength += ModPlugin.getShipStrength(fm);
                        selectedForDeployment.add(fm);
                    }

                    //Global.getLogger(this.getClass()).info("selectedStrength: " + selectedStrength);
                } else if (e.isKeyDownEvent() && e.getEventValue() == ESCAPE_KEY_VALUE) {
                    escapeMenuIsOpen = true;
                }
            }

            if (!Mouse.isButtonDown(0)) { // Mouse up event is consumed at the end of a button press
                if (mouseDownOverAllBtn && mouseIsOverAllButton()) {
                    if (selectedForDeployment.size() == pf.getReservesCopy().size()) {
                        selectedForDeployment.clear();
                        selectedStrength = 0;
                    } else {
                        boolean noCombatShipsAdded = true;

                        for (FleetMemberAPI ship : pf.getReservesCopy()) {
                            if (!selectedForDeployment.contains(ship) && !ship.isCivilian()
                                    && ship.canBeDeployedForCombat() && !ship.isMothballed()) {

                                noCombatShipsAdded = false;
                                selectedStrength += ModPlugin.getShipStrength(ship);
                                selectedForDeployment.add(ship);
                            }
                        }

                        if (noCombatShipsAdded) {
                            for (FleetMemberAPI ship : pf.getReservesCopy()) {
                                if (!selectedForDeployment.contains(ship)) {
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

    void drawDebugShip() {
        FleetMemberAPI ship = getShipUnderCursor();

        //Global.getLogger(this.getClass()).info("isUIShowingDialog");

        if (ship != null) {
            //Global.getLogger(this.getClass()).info("ship != null");

            SpriteAPI sprite = Global.getSettings().getSprite(ship.getHullSpec().getSpriteName());

            sprite.setWidth(sprite.getWidth() * PIXEL_WIDTH);
            sprite.setHeight(sprite.getHeight() * PIXEL_HEIGHT);


            sprite.render(-0.8f, 0);
        }
    }

    void drawOldDpIndicators() {
        //            Global.getLogger(this.getClass()).info("\nengine.isUIShowingDialog(): " + engine.isUIShowingDialog()
//                    + "engine.getCombatUI().isShowingCommandUI(): " + engine.getCombatUI().isShowingCommandUI()
//                    + "engine.isUIShowingHUD()" + engine.isUIShowingHUD());

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
//            GL11.glEnable(GL11.GL_POINT_SMOOTH);
//            GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

//            float x = engine.getViewport().getVisibleWidth() * 0.5f - BAR_WIDTH - 4;
//            float y = engine.getViewport().getVisibleHeight() * 0.5f - 201;
//            float hw = 1, hh = 1;

//            Global.getLogger(this.getClass()).info(String.format("w:%s   h:%s", hw, hh));

        Color color = new Color(69, 152, 176), color2 = Color.BLACK;

        Color clr50 = new Color(64, 255, 32),
                clr75 = new Color(128, 255, 128),
                clr100 = new Color(255, 255, 128),
                clr125 = new Color(255, 128, 128),
                clr150 = new Color(255, 64, 32);




        int totalDpUsed = 0;

        for(DeployedFleetMemberAPI dfm : pf.getAllEverDeployed()) {
            totalDpUsed += (int)dfm.getMember().getDeploymentPointsCost();
        }

//            Global.getLogger(this.getClass()).info(
//                    "\nMax DP: " + pf.getMaxFleetPoints().getModifiedInt() +
//                            "\nAvailable DP: " + pf.getAvailableFleetPoints() +
//                            "\nTotal Used DP: " + totalDpUsed);

        float pos = (float)totalDpUsed / pf.getMaxFleetPoints().getModifiedInt();

//            drawIndicator(0.3f,  2, clr150);
//            drawIndicator(0.4f, 3, clr125);
//            drawIndicator(0.5f,  4, clr100);
//            drawIndicator(0.6f, 3, clr75);
//            drawIndicator(0.7f,  2, clr50);

//            drawIndicator(0.3f,  3, color, 13);
//            drawIndicator(0.3f,  2, color2, 13);
//
//            drawIndicator(0.4f, 4, color, 13);
//            drawIndicator(0.4f, 3, color2, 13);
//            drawIndicator(0.5f,  1.8f, color, 13);

        drawIndicator(pos,  5, color, 13);
        drawIndicator(pos,  5, color2, 14);
        drawIndicator(pos,  2, clr100, 13);

//            drawIndicator(0.6f, 4, color, 13);
//            drawIndicator(0.6f, 3, color2, 13);
//            drawIndicator(0.5f,  1.8f, color, 13);

//            drawIndicator(0.7f,  3, color, 13);
//            drawIndicator(0.7f,  2, color2, 13);
    }

    void drawIndicator(float position, float size, Color color, float heightSpace) {
        if(position < 0 || position > 1 || timeShowingDeployMenu < INDICATOR_DELAY) return;

        float x = -BAR_WIDTH * (1 - position) - 4;
//        float hw = engine.getViewport().getVisibleWidth() * 0.5f;
//        float hh = engine.getViewport().getVisibleHeight() * 0.5f;

        GL11.glColor4ub((byte)color.getRed(),
                (byte)color.getGreen(),
                (byte)color.getBlue(),
                (byte)(color.getAlpha() * Math.min(1, (timeShowingDeployMenu - INDICATOR_DELAY) * 5)));

        GL11.glBegin(GL_TRIANGLES); {
            GL11.glVertex2f(x * PIXEL_WIDTH, (Y_POS - (heightSpace - size)) * PIXEL_HEIGHT);
            GL11.glVertex2f((x - size) * PIXEL_WIDTH, (Y_POS - (heightSpace + 0)) * PIXEL_HEIGHT);
            GL11.glVertex2f((x + size) * PIXEL_WIDTH, (Y_POS - (heightSpace + 0)) * PIXEL_HEIGHT);

            GL11.glVertex2f(x * PIXEL_WIDTH, (Y_POS + (heightSpace - size)) * PIXEL_HEIGHT);
            GL11.glVertex2f((x - size) * PIXEL_WIDTH, (Y_POS + (heightSpace + 0)) * PIXEL_HEIGHT);
            GL11.glVertex2f((x + size) * PIXEL_WIDTH, (Y_POS + (heightSpace + 0)) * PIXEL_HEIGHT);
        } GL11.glEnd();
    }
}
