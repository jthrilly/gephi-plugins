package uk.ac.ox.oii.sigmaexporter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;

class SigmaSettingsPanelTest {

    /** GroupLayout throws if a component is missing from either axis; this catches hand-edit mistakes. */
    @Test
    void panelLaysOut() {
        System.setProperty("java.awt.headless", "true");
        SigmaSettingsPanel panel = new SigmaSettingsPanel();
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        panel.doLayout();
        assertTrue(size.width > 0 && size.height > 0);
    }
}
