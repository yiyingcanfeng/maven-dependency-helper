package actions;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * @author z
 */
@State(
        name = "MavenDependencyHelper",
        storages = {@Storage("MavenDependencyHelper.xml")}
)
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {

    public int speedDetectInterval = 600;

    public static AppSettingsState getInstance() {
        // ServiceManager.getService(Class) is deprecated after 2021.2.1
        String fullVersion = ApplicationInfo.getInstance().getFullVersion();
        AppSettingsState service = null;
        if (Integer.parseInt(fullVersion.replace(".", "")) <= 202113) {
            try {
                service = (AppSettingsState) Class.forName("com.intellij.openapi.components.ServiceManager")
                        .getMethod("getService", Class.class)
                        .invoke(null, AppSettingsState.class);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Class<?> applicationManagerClass = Class.forName("com.intellij.openapi.application.ApplicationManager");

                Object applicationInvoke = applicationManagerClass.getMethod("getApplication").invoke(null);
                service = (AppSettingsState) applicationInvoke.getClass().getMethod("getService", Class.class).invoke(applicationInvoke, AppSettingsState.class);

            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return service;
    }

    @Nullable
    @Override
    public AppSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
