/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.application.options;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import consulo.application.ApplicationBundle;
import consulo.language.codeStyle.ui.setting.OptionTreeWithPreviewPanel;
import com.intellij.java.language.impl.JavaFileType;
import consulo.language.Language;
import com.intellij.java.language.JavaLanguage;
import consulo.ui.ex.awt.OnePixelDivider;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.codeStyle.CodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import consulo.language.codeStyle.setting.LanguageCodeStyleSettingsProvider;
import consulo.ui.ex.awt.CustomLineBorder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
public class JavaDocFormattingPanel extends OptionTreeWithPreviewPanel {
    private JCheckBox myEnableCheckBox;

    private final JPanel myJavaDocPanel = new JPanel(new BorderLayout());
    public static final String OTHER_GROUP = ApplicationBundle.message("group.javadoc.other");
    public static final String INVALID_TAGS_GROUP = ApplicationBundle.message("group.javadoc.invalid.tags");
    public static final String BLANK_LINES_GROUP = ApplicationBundle.message("group.javadoc.blank.lines");
    public static final String ALIGNMENT_GROUP = ApplicationBundle.message("group.javadoc.alignment");

    public JavaDocFormattingPanel(CodeStyleSettings settings) {
        super(settings);
        init();
    }

    @Override
    protected void init() {
        super.init();

        myEnableCheckBox = new JCheckBox(ApplicationBundle.message("checkbox.enable.javadoc.formatting"));
        myEnableCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                update();
            }
        });

        myPanel.setBorder(new CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0));
        myJavaDocPanel.add(BorderLayout.CENTER, myPanel);
        myJavaDocPanel.add(myEnableCheckBox, BorderLayout.NORTH);
    }

    @Override
    public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
        return LanguageCodeStyleSettingsProvider.SettingsType.LANGUAGE_SPECIFIC;
    }

    @Override
    public JComponent getPanel() {
        return myJavaDocPanel;
    }

    private void update() {
        setEnabled(getPanel(), myEnableCheckBox.isSelected());
        myEnableCheckBox.setEnabled(true);
    }

    @Override
    protected void initTables() {
        initCustomOptions(ALIGNMENT_GROUP);
        initCustomOptions(BLANK_LINES_GROUP);
        initCustomOptions(INVALID_TAGS_GROUP);
        initBooleanField("WRAP_COMMENTS", ApplicationBundle.message("checkbox.wrap.at.right.margin"), OTHER_GROUP);
        initCustomOptions(OTHER_GROUP);
    }

    @Override
    protected int getRightMargin() {
        return 47;
    }

    @Override
    protected String getPreviewText() {                    //| Margin is here
        return "package sample;\n" +
            "public class Sample {\n" +
            "  /**\n" +
            "   * This is a method description that is long enough to exceed right margin.\n" +
            "   *\n" +
            "   * Another paragraph of the description placed after blank line.\n" +
            "   * <p/>\n" +
            "   * Line with manual\n" +
            "   * line feed.\n" +
            "   * @param i short named parameter description\n" +
            "   * @param longParameterName long named parameter description\n" +
            "   * @param missingDescription\n" +
            "   * @return return description.\n" +
            "   * @throws XXXException description.\n" +
            "   * @throws YException description.\n" +
            "   * @throws ZException\n" +
            "   *\n" +
            "   * @invalidTag" +
            "   */\n" +
            "  public abstract String sampleMethod(int i, int longParameterName, int missingDescription) throws XXXException, YException, ZException;\n" +
            "\n" +
            "  /** One-line comment */\n" +
            "  public abstract String sampleMethod2();\n" +
            "\n" +
            "  /**\n" +
            "   * Simple method description\n" +
            "   * @return\n" +
            "   */\n" +
            "  public abstract String sampleMethod3();\n";
    }


    private static void setEnabled(JComponent c, boolean enabled) {
        c.setEnabled(enabled);
        Component[] children = c.getComponents();
        for (Component child : children) {
            if (child instanceof JComponent) {
                setEnabled((JComponent)child, enabled);
            }
        }
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        super.apply(settings);
        settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING = myEnableCheckBox.isSelected();
    }

    @Override
    protected void resetImpl(final CodeStyleSettings settings) {
        super.resetImpl(settings);
        myEnableCheckBox.setSelected(settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING);
        update();
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        return super.isModified(settings) ||
            myEnableCheckBox.isSelected() != settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING;
    }

    @Override
    @Nonnull
    protected final FileType getFileType() {
        return JavaFileType.INSTANCE;
    }

    @Override
    protected void customizeSettings() {
        LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE);
        if (provider != null) {
            provider.customizeSettings(this, getSettingsType());
        }
    }

    @Override
    protected String getTabTitle() {
        return ApplicationBundle.message("title.javadoc");
    }

    @Nullable
    @Override
    public Language getDefaultLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
