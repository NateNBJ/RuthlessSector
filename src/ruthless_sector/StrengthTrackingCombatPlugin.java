package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.*;

public class StrengthTrackingCombatPlugin implements EveryFrameCombatPlugin {
    static final float
            PIXEL_WIDTH = 1 / (Global.getSettings().getScreenWidth() * 0.5f),
            PIXEL_HEIGHT = 1 / (Global.getSettings().getScreenHeight() * 0.5f);
    static final Color clr1 = new Color(155, 255, 0),
            clr2 = new Color(205, 233, 0),
            clr3 = new Color(255, 210, 0),
            clr4 = new Color(255, 155, 0),
            clr5 = new Color(255, 100, 0);

    SpriteAPI sprite, sprite2;
    CombatFleetManager pf, ef;
    Set<FleetMemberAPI> selectedForDeployment = new HashSet<>();
    Map<FleetMemberAPI, Integer> clickCount = new HashMap<>();
    CombatEngineAPI engine;


    float deployedStrength = Float.MIN_VALUE, selectedStrength = 0;
    boolean noMothballedShipsAreReserved = true, scheduleSelectionUpdate = true;
    int  limit;

    boolean isIrrelevant() { return !ModPlugin.SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN || engine == null
            || !engine.isInCampaign() || engine.isSimulation() || engine.isInCampaignSim(); }


    @Override
    public void init(CombatEngineAPI engine) {
        try {
            if(engine == null || !engine.isInCampaign() || engine.isSimulation() || engine.isInCampaignSim()) return;

            this.engine = engine;
            ef = CombatEngine.getInstance().getFleetManager(1);
            pf = CombatEngine.getInstance().getFleetManager(0);

            if (CampaignScript.timeHasPassedSinceLastEngagement) { // Then this is the start of a new battle
                CampaignScript.timeHasPassedSinceLastEngagement = false;
                ModPlugin.battleStartedSinceLastSave = true;
                float efStrength = 0;
                for (FleetMemberAPI fm : ef.getReservesCopy()) efStrength += ModPlugin.getShipStrength(fm, false);
                for (FleetMemberAPI fm : ef.getDeployedCopy()) efStrength += ModPlugin.getShipStrength(fm, false);

                ModPlugin.resetIntegrationValues();
                ModPlugin.updateEnemyStrength(efStrength);

                if(ModPlugin.battlesResolvedSinceLastSave == 0) {
                    ModPlugin.adjustReloadPenalty(ModPlugin.RELOAD_PENALTY_PER_RELOAD);
                }
            }

            if (ModPlugin.SHOW_BATTLE_DIFFICULTY_STARS_ON_DEPLOYMENT_SCREEN) {
                deployedStrength = Float.MIN_VALUE;
                selectedStrength = 0;
                limit = engine.getFleetManager(FleetSide.PLAYER).getMaxStrength();

                sprite = Global.getSettings().getSprite("ui", "icon_fleet_danger");
                sprite.setWidth(sprite.getWidth() * PIXEL_WIDTH);
                sprite.setHeight(sprite.getHeight() * PIXEL_HEIGHT);
                sprite2 = Global.getSettings().getSprite("ui", "icon_fleet_danger");
                sprite2.setWidth(sprite2.getWidth() * PIXEL_WIDTH);
                sprite2.setHeight(sprite2.getHeight() * PIXEL_HEIGHT);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void advance(float amount, java.util.List<InputEventAPI> events) {
        try {
            if (isIrrelevant()) return;

            if(engine.getCombatUI().isShowingDeploymentDialog()) {
                if(deployedStrength == Float.MIN_VALUE) {
                    limit = engine.getFleetManager(FleetSide.PLAYER).getMaxStrength();

                    Set<FleetMemberAPI> deployedShips = new HashSet<> ();

                    deployedShips.addAll(pf.getDeployedCopy());
                    deployedShips.addAll(pf.getRetreatedCopy());
                    deployedShips.addAll(pf.getDisabledCopy());
                    deployedShips.addAll(pf.getDestroyedCopy());

                    for(FleetMemberAPI ship : deployedShips) deployedStrength += ModPlugin.getShipStrength(ship, true);

                    noMothballedShipsAreReserved = true;

                    for(FleetMemberAPI ship : pf.getCampaignFleet().getMembers()) {
                        if(ship.isMothballed()) {
                            noMothballedShipsAreReserved = false;
                            break;
                        }
                    }
                }

                if(scheduleSelectionUpdate) {
                    selectedStrength = 0;

                    for(FleetMemberAPI ship : engine.getCombatUI().getCurrentlySelectedInFleetDeploymentDialog()) {
                        selectedStrength += ModPlugin.getShipStrength(ship, true);
                    }
                }

                render();
            } else {
                deployedStrength = Float.MIN_VALUE;
                selectedStrength = 0;
                selectedForDeployment.clear();
                clickCount.clear();
            }

        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    public void render() {
        double pfStrength = Math.max(deployedStrength + selectedStrength, ModPlugin.playerStrength.val);
        float danger = CampaignScript.getDanger(pfStrength, ModPlugin.enemyStrength.val);
        int stars = (int)Math.ceil(danger);
        float xSpacing = sprite.getWidth() + PIXEL_WIDTH * 2,
                y = (Global.getSettings().getScreenHeight() * 0.5f - 10) * PIXEL_HEIGHT - sprite.getHeight();
                //y = PIXEL_HEIGHT * 244f;

        switch (stars) {
            case 1: sprite.setColor(clr1); break;
            case 2: sprite.setColor(clr2); break;
            case 3: sprite.setColor(clr3); break;
            case 4: sprite.setColor(clr4); break;
            default: sprite.setColor(clr5); break;
        }

        if(danger > 5) {
            float whiteness = (float)Math.sin(engine.getTotalElapsedTime(true) * 10) * 0.15f + 0.15f;
            Color clr = sprite.getColor();
            sprite.setColor(new Color(
                    (int)(clr.getRed() + (255f - clr.getRed()) * whiteness),
                    (int)(clr.getGreen() + (255f - clr.getGreen()) * whiteness),
                    (int)(clr.getBlue() + (255f - clr.getBlue()) * whiteness)
            ));
        }

        for(int i = 0; i < stars; ++i) {
            float x = 10 * PIXEL_WIDTH + i * xSpacing - 1f;
            //float x = -292 * PIXEL_WIDTH + i * xSpacing;

            if(danger - i < 1) {
                sprite2.setColor(sprite.getColor());
                sprite2.setAlphaMult(danger - i);
                sprite2.render(x, y);
            } else {
                sprite.render(x, y);
            }
        }
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        if(engine != null && engine.getCombatUI() != null) {
            scheduleSelectionUpdate = engine.getCombatUI().isShowingDeploymentDialog();
        }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

//    protected CustomPanelAPI panel = null;

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
//        try {
//            if(true) return;
//
//            if (isIrrelevant() || !engine.getCombatUI().isShowingDeploymentDialog()) return;
//
//            if (panel == null) {
//                panel = Global.getSettings().createCustom(600, 100, new BaseCustomUIPanelPlugin() {
//                    @Override
//                    public void buttonPressed(Object buttonId) {
//                        System.out.println("BUTTON PRESSED: " + buttonId);
//                    }
//                });
//                TooltipMakerAPI t = panel.createUIElement(600, 100, false);
//                t.addPara("TEST", 0);
////                t.addButton("TEST", "TEST", 200, 20, 0f);
//                panel.addUIElement(t).inTL(0, 0);
//                panel.getPosition().setLocation(10, 20 + sprite.getWidth());
//                panel.getPosition().setSize(600, 100);
//            }
//
//            //renderQuad(100, 100, 200, 200, Color.white, 1f);
//            PositionAPI p = panel.getPosition();
//            float x = p.getX();
//            float y = p.getY();
//            float w = p.getWidth();
//            float h = p.getHeight();
//            renderQuad(x, y, w, h, Color.gray, 1.0f);
//
//            panel.render(1f);
//
//        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    public static void renderQuad(float x, float y, float width, float height, Color color, float alphaMult) {

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        //System.out.println((float)color.getAlpha() * alphaMult);
        GL11.glColor4ub((byte)color.getRed(),
                (byte)color.getGreen(),
                (byte)color.getBlue(),
                (byte)((float)color.getAlpha() * alphaMult));

        GL11.glBegin(GL11.GL_QUADS);
        {
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + height);
            GL11.glVertex2f(x + width, y + height);
            GL11.glVertex2f(x + width, y);
        }
        GL11.glEnd();
    }
}
