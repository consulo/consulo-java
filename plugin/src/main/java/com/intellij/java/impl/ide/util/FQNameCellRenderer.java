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
package com.intellij.java.impl.ide.util;

import consulo.application.AllIcons;
import consulo.logging.Logger;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import com.intellij.java.language.psi.PsiClass;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.language.icon.IconDescriptorUpdaters;

import javax.swing.*;
import java.awt.*;

public class FQNameCellRenderer extends SimpleColoredComponent implements ListCellRenderer{
  private final Font FONT;
  private static final Logger LOG = Logger.getInstance(FQNameCellRenderer.class);

  public FQNameCellRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    setOpaque(true);
  }

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus){

    clear();

    if (value instanceof PsiClass) {
      PsiClass aClass = (PsiClass)value;
      setIcon(IconDescriptorUpdaters.getIcon(aClass, 0));
      if (aClass.getQualifiedName() != null) {
        SimpleTextAttributes attributes;
        if (aClass.isDeprecated()) {
          attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);
        }
        else {
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        }
        append(aClass.getQualifiedName(), attributes);
      }
    }
    else {
      LOG.assertTrue(value instanceof String);
      String qName = (String)value;
      append(qName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setIcon(AllIcons.Nodes.Static);
    }

    setFont(FONT);
    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    }
    else {
      setBackground(list.getBackground());
      setForeground(list.getForeground());
    }
    return this;
  }
}
