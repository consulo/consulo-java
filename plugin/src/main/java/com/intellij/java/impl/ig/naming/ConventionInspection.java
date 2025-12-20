/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.ui.BlankFiller;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.ide.impl.idea.util.ui.RegExFormatter;
import consulo.ide.impl.idea.util.ui.RegExInputVerifier;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.xml.serializer.InvalidDataException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConventionInspection extends BaseInspection {

  /**
   * public fields for the DefaultJDomExternalizer
   *
   * @noinspection PublicField
   */
  public String m_regex = getDefaultRegex();
  /**
   * @noinspection PublicField
   */
  public int m_minLength = getDefaultMinLength();
  /**
   * @noinspection PublicField
   */
  public int m_maxLength = getDefaultMaxLength();
  protected Pattern m_regexPattern = Pattern.compile(m_regex);

  @NonNls
  protected abstract String getDefaultRegex();

  protected abstract int getDefaultMinLength();

  protected abstract int getDefaultMaxLength();

  protected String getRegex() {
    return m_regex;
  }

  protected int getMinLength() {
    return m_minLength;
  }

  protected int getMaxLength() {
    return m_maxLength;
  }

  protected boolean isValid(String name) {
    int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (length > m_maxLength) {
      return false;
    }
    if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(name)) {
      return true;
    }
    Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  private static final int REGEX_COLUMN_COUNT = 25;

  public Collection<? extends JComponent> createExtraOptions() {
    return Collections.emptyList();
  }

  @Override
  public final JComponent createOptionsPanel() {
    GridBagLayout layout = new GridBagLayout();
    JPanel panel = new JPanel(layout);

    JLabel patternLabel = new JLabel(InspectionGadgetsLocalize.conventionPatternOption().get());
    JLabel minLengthLabel = new JLabel(InspectionGadgetsLocalize.conventionMinLengthOption().get());
    JLabel maxLengthLabel = new JLabel(InspectionGadgetsLocalize.conventionMaxLengthOption().get());

    NumberFormat numberFormat = NumberFormat.getIntegerInstance();
    numberFormat.setParseIntegerOnly(true);
    numberFormat.setMinimumIntegerDigits(1);
    numberFormat.setMaximumIntegerDigits(2);
    InternationalFormatter formatter =
      new InternationalFormatter(numberFormat);
    formatter.setAllowsInvalid(true);
    formatter.setCommitsOnValidEdit(true);

    final JFormattedTextField minLengthField =
      new JFormattedTextField(formatter);
    Font panelFont = panel.getFont();
    minLengthField.setFont(panelFont);
    minLengthField.setValue(Integer.valueOf(m_minLength));
    minLengthField.setColumns(2);
    UIUtil.fixFormattedField(minLengthField);

    final JFormattedTextField maxLengthField =
      new JFormattedTextField(formatter);
    maxLengthField.setFont(panelFont);
    maxLengthField.setValue(Integer.valueOf(m_maxLength));
    maxLengthField.setColumns(2);
    UIUtil.fixFormattedField(minLengthField);

    final JFormattedTextField regexField =
      new JFormattedTextField(new RegExFormatter());
    regexField.setFont(panelFont);
    regexField.setValue(m_regexPattern);
    regexField.setColumns(REGEX_COLUMN_COUNT);
    regexField.setInputVerifier(new RegExInputVerifier());
    regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
    UIUtil.fixFormattedField(minLengthField);
    DocumentListener listener = new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent evt) {
        try {
          regexField.commitEdit();
          minLengthField.commitEdit();
          maxLengthField.commitEdit();
          m_regexPattern = (Pattern)regexField.getValue();
          m_regex = m_regexPattern.pattern();
          m_minLength = ((Number)minLengthField.getValue()).intValue();
          m_maxLength = ((Number)maxLengthField.getValue()).intValue();
        }
        catch (ParseException e) {
          // No luck this time
        }
      }
    };
    Document regexDocument = regexField.getDocument();
    regexDocument.addDocumentListener(listener);
    Document minLengthDocument = minLengthField.getDocument();
    minLengthDocument.addDocumentListener(listener);
    Document maxLengthDocument = maxLengthField.getDocument();
    maxLengthDocument.addDocumentListener(listener);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(patternLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(regexField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    panel.add(minLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 1;
    constraints.weightx = 1;
    constraints.insets.right = 0;
    panel.add(minLengthField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.weightx = 0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    panel.add(maxLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 2;
    constraints.weightx = 1;
    constraints.insets.right = 0;
    panel.add(maxLengthField, constraints);

    Collection<? extends JComponent> extraOptions =
      createExtraOptions();
    constraints.gridx = 0;
    constraints.gridwidth = 2;
    for (JComponent extraOption : extraOptions) {
      constraints.gridy++;
      panel.add(extraOption, constraints);
    }

    constraints.gridy++;
    constraints.weighty = 1.0;
    panel.add(new BlankFiller(), constraints);

    return panel;
  }
}
