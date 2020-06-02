package actions;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import searcher.DependencySearcherManager;
import ui.AppSettingsForm;

import javax.swing.*;

/**
 * @author z
 */
public class ApplicationSettingsConfigurable implements Configurable {

    private AppSettingsForm form;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MavenDependencyHelper";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return form.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        form = new AppSettingsForm();
        AppSettingsState settings = AppSettingsState.getInstance();
        form.setIntervalText(settings.speedDetectInterval + "");
        return form.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingsState settings = AppSettingsState.getInstance();
        try {
            return Integer.parseInt(form.getIntervalText()) != settings.speedDetectInterval;
        }catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        AppSettingsState settings = AppSettingsState.getInstance();
        try {
            settings.speedDetectInterval = Integer.parseInt(form.getIntervalText());
            DependencySearcherManager.setSpeedTestInterval(settings.speedDetectInterval);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void reset() {
        AppSettingsState settings = AppSettingsState.getInstance();
        form.setIntervalText(settings.speedDetectInterval + "");
        DependencySearcherManager.setSpeedTestInterval(settings.speedDetectInterval);
    }

    @Override
    public void disposeUIResources() {
        form = null;
    }
}
