package ruthless_sector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class CombatPlugin implements EveryFrameCombatPlugin {
    boolean domainDronesNeedBonus = true;
    //SpriteAPI sprite = Global.getSettings().getSprite("graphics/ships/wolf_ff.png");

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            CombatEngineAPI engine = Global.getCombatEngine();

//            render();

//            Vector2f zero = new Vector2f(0, 0);
//            MagicRender.screenspace(sprite, MagicRender.positioning.CENTER, zero, zero, zero, zero, 0, 0, Color.WHITE, false, 1, 1, 1);

            if (engine == null || engine.getShips() == null) return;


//            ViewportAPI vp = engine.getViewport();
//            vp.setExternalControl(true);
//            //vp.setCenter(engine.getPlayerShip().getCopyLocation());
//            vp.set(90, 20, vp.getVisibleWidth(), vp.getVisibleHeight());


            if(domainDronesNeedBonus && engine.getTotalElapsedTime(false) > 1) {
                for(ShipAPI ship : engine.getShips()) {
                    //Global.getLogger(this.getClass()).info(ship.getHullStyleId() + " " + ship.getMutableStats().getCRLossPerSecondPercent().computeEffective(100));

                    if(ship.getHullSpec().getManufacturer().equals("Explorarium")) {
                        ship.getMutableStats().getEnergyWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getBallisticWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                        ship.getMutableStats().getMissileWeaponRangeBonus().modifyMult("rs_derelict_bonus", ModPlugin.RANGE_MULT_FOR_AUTOMATED_DEFENSES);
                    }
                }
                domainDronesNeedBonus = false;
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        // TODO - check for ESC and set flag

//        for (InputEventAPI event : events) {
//            if (event.isConsumed()) continue;
//
//            if (event.isMouseMoveEvent()) {
//                mouseX = event.getX();
//                mouseY = event.getY();
//            }
//        }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) { }

    private void render() {
//        CombatEngineAPI engine = Global.getCombatEngine();
//
//        if(engine.isUIShowingDialog()) { // and the dialog isn't the escape menu
//
////            Global.getLogger(this.getClass()).info("\nengine.isUIShowingDialog(): " + engine.isUIShowingDialog()
////                    + "engine.getCombatUI().isShowingCommandUI(): " + engine.getCombatUI().isShowingCommandUI()
////                    + "engine.isUIShowingHUD()" + engine.isUIShowingHUD());
//
//
//
//            GL11.glDisable(GL11.GL_TEXTURE_2D);
//            GL11.glEnable(GL11.GL_BLEND);
//            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//
//            float x = -0.5f;
//            float y = -0.5f;
//            float w = 1;
//            float h = 1;
//
//            Color color = Color.WHITE;
//
//            GL11.glColor4ub((byte)color.getRed(),
//                    (byte)color.getGreen(),
//                    (byte)color.getBlue(),
//                    (byte)(color.getAlpha() * 1));
//
//            GL11.glBegin(GL_TRIANGLES);
//            {
//                GL11.glVertex2f(0, -0.1f);
//                GL11.glVertex2f(-0.005f, -0.11f);
//                GL11.glVertex2f(0.005f, -0.11f);
//            }
//            GL11.glEnd();
//
//        }
    }

    private float mouseX, mouseY;
    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        CombatEngineAPI engine = Global.getCombatEngine();

        if(true || engine.isUIShowingDialog()) { // and the dialog isn't the escape menu
//            glPushAttrib(GL_ALL_ATTRIB_BITS);
//            glMatrixMode(GL_PROJECTION);
//            glPushMatrix();
//            glLoadIdentity();
//
//            int width = (int) (Display.getWidth() * Display.getPixelScaleFactor()),
//                    height = (int) (Display.getHeight() * Display.getPixelScaleFactor());
//            glViewport(0, 0, width, height);
//            glOrtho(0, width, 0, height, -1, 1);
//
//            glMatrixMode(GL_MODELVIEW);
//            glPushMatrix();
//            glLoadIdentity();
//            glDisable(GL_TEXTURE_2D);
//            glEnable(GL_BLEND);
//            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//            glTranslatef(0.01f, 0.01f, 0);
//
//            // Set up the stencil test
//            glClear(GL_STENCIL_BUFFER_BIT);
//            glEnable(GL_STENCIL_TEST);
//            glColorMask(false, false, false, false);
//            glStencilFunc(GL_ALWAYS, 1, 1);
//            glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
//            glColorMask(true, true, true, true);
//
//
//            sprite.setAlphaMult(1);
//            sprite.renderAtCenter(mouseX, mouseY);
//            sprite.renderAtCenter(0, 0);
//            sprite.renderAtCenter(0.5f, 0.5f);
//            sprite.renderAtCenter(0.5f, -0.5f);
//            sprite.renderAtCenter(-0.5f, 0.5f);
//            sprite.renderAtCenter(-0.5f, -0.5f);
//
//
//            sprite.render(mouseX, mouseY);
//            sprite.render(0, 0);
//            sprite.render(0.5f, 0.5f);
//            sprite.render(0.5f, -0.5f);
//            sprite.render(-0.5f, 0.5f);
//            sprite.render(-0.5f, -0.5f);
//
//
//
//            // Finalize drawing
//            glDisable(GL_BLEND);
//            glPopMatrix();
//            glMatrixMode(GL_PROJECTION);
//            glPopMatrix();
//            glPopAttrib();




//            GL11.glDisable(GL11.GL_TEXTURE_2D);
//            GL11.glEnable(GL11.GL_BLEND);
//            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//
//            float x = -0.5f;
//            float y = -0.5f;
//            float w = 1;
//            float h = 1;
//
//            Color color = Color.WHITE;
//
//            GL11.glColor4ub((byte) color.getRed(),
//                    (byte) color.getGreen(),
//                    (byte) color.getBlue(),
//                    (byte) (color.getAlpha() * 1));
//
//            GL11.glBegin(GL_TRIANGLES);
//            {
//                GL11.glVertex2f(0, 0);
//                GL11.glVertex2f(-0.005f, -0.01f);
//                GL11.glVertex2f(0.005f, -0.01f);
//            }
//            GL11.glEnd();

//            glPushAttrib(GL_ALL_ATTRIB_BITS);
//            glMatrixMode(GL_PROJECTION);
//            glPushMatrix();
//            glLoadIdentity();
//            glMatrixMode(GL_MODELVIEW);
//            glPushMatrix();
//            glLoadIdentity();
//            glDisable(GL_TEXTURE_2D);
//            glEnable(GL_BLEND);
//            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//            glTranslatef(0.01f, 0.01f, 0);
//
//            sprite.bindTexture();
//            GL11.glBegin(GL_QUADS);
//            GL11.glTexCoord2f(0f, 0f);
//            GL11.glVertex2f(0f, 0f);
//            GL11.glTexCoord2f(sprite.getWidth(), 0f);
//            GL11.glVertex2f(sprite.getWidth(), 0f);
//            GL11.glTexCoord2f(sprite.getWidth(), sprite.getHeight());
//            GL11.glVertex2f(sprite.getWidth(), sprite.getHeight());
//            GL11.glTexCoord2f(0f, sprite.getHeight());
//            GL11.glVertex2f(0f, sprite.getHeight());
//            GL11.glEnd();
//
//            // Finalize drawing
//            glDisable(GL_BLEND);
//            glPopMatrix();
//            glMatrixMode(GL_PROJECTION);
//            glPopMatrix();
//            glPopAttrib();

//            sprite.setAlphaMult(1);
//            sprite.renderAtCenter(mouseX, mouseY);
//            sprite.renderAtCenter(0, 0);
//            sprite.renderAtCenter(0.5f, 0.5f);
//            sprite.renderAtCenter(0.5f, -0.5f);
//            sprite.renderAtCenter(-0.5f, 0.5f);
//            sprite.renderAtCenter(-0.5f, -0.5f);
//
//
//            sprite.render(mouseX, mouseY);
//            sprite.render(0, 0);
//            sprite.render(0.5f, 0.5f);
//            sprite.render(0.5f, -0.5f);
//            sprite.render(-0.5f, 0.5f);
//            sprite.render(-0.5f, -0.5f);
        }
    }

    @Override
    public void init(CombatEngineAPI engine) { }
}
