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
package com.intellij.java.impl.refactoring.ui;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import consulo.component.util.Iconable;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.language.icon.IconDescriptorUpdaters;

/**
 *  @author dsl
 */
public class MethodCellRenderer extends DefaultListCellRenderer {
  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    PsiMethod method = (PsiMethod) value;

    String text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
              PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
              PsiFormatUtil.SHOW_TYPE);
    setText(text);

    setIcon(TargetAWT.to(IconDescriptorUpdaters.getIcon(method, Iconable.ICON_FLAG_VISIBILITY)));
    return this;
  }

}
