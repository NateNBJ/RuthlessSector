package ruthless_sector;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

import static ruthless_sector.ModPlugin.LUNALIB_ID;

public class LunaSettingsChangedListener implements LunaSettingsListener {
    @Override
    public void settingsChanged(String idOfModWithChangedSettings) {
        if(idOfModWithChangedSettings.equals(ModPlugin.ID)) {
            try {
                ModPlugin.readSettings();
            } catch (Exception e) {
                ModPlugin.reportCrash(e);
            }
        }
    }
    public static void addToManagerIfNeeded() {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)
                && !LunaSettings.INSTANCE.hasListenerOfClass(LunaSettingsChangedListener.class)) {

            LunaSettings.INSTANCE.addListener(new LunaSettingsChangedListener());
        }
    }
}
