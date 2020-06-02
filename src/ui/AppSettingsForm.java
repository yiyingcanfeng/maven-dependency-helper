package ui;

import javax.swing.*;

/**
 * @author z
 */
public class AppSettingsForm {

    private JTextField textField;
    private JPanel panel;

    public JPanel getPanel() {
        return panel;
    }

    public void setIntervalText(String text) {
        textField.setText(text);
    }

    public String getIntervalText() {
        return textField.getText();
    }

    public JComponent getPreferredFocusedComponent() {
        return textField;
    }
}
