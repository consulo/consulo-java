/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.unusedSymbol;

import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.UserActivityProviderComponent;
import consulo.ui.ex.awt.ClickListener;
import consulo.ui.ex.awt.JBCurrentTheme;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class VisibilityModifierChooser extends JLabel implements UserActivityProviderComponent {
  @PsiModifier.ModifierConstant
  private static final String[] MODIFIERS = new String[]{
      PsiModifier.PRIVATE,
      PsiModifier.PACKAGE_LOCAL,
      PsiModifier.PROTECTED,
      PsiModifier.PUBLIC
  };
  private final Supplier<Boolean> myCanBeEnabled;

  private final Set<ChangeListener> myListeners = new HashSet<>();
  private String myCurrentModifier;

  public VisibilityModifierChooser(@Nonnull Supplier<Boolean> canBeEnabled, @Nonnull String modifier, @Nonnull Consumer<String> modifierChangedConsumer) {
    this(canBeEnabled, modifier, modifierChangedConsumer, MODIFIERS);
  }


  @Override
  public void setText(String text) {
    super.setText(text);
  }

  public VisibilityModifierChooser(@Nonnull Supplier<Boolean> canBeEnabled, @Nonnull String modifier, @Nonnull Consumer<String> modifierChangedConsumer, @Nonnull String[] modifiers) {
    myCanBeEnabled = canBeEnabled;
    setIcon(TargetAWT.to(PlatformIconGroup.generalArrowdown()));
    setDisabledIcon(TargetAWT.to(PlatformIconGroup.generalArrowdown()));
    setIconTextGap(0);
    setHorizontalTextPosition(SwingConstants.LEFT);
    myCurrentModifier = modifier;
    setText(getPresentableText(myCurrentModifier));
    new ClickListener() {
      @Override
      public boolean onClick(@Nonnull MouseEvent e, int clickCount) {
        if (!isEnabled()) {
          return true;
        }
        @SuppressWarnings("UseOfObsoleteCollectionType") Hashtable<Integer, JComponent> sliderLabels = new Hashtable<>();
        for (int i = 0; i < modifiers.length; i++) {
          sliderLabels.put(i + 1, new JLabel(getPresentableText(modifiers[i])));
        }

        JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);
        slider.addChangeListener(val ->
        {
          final String modifier = modifiers[slider.getValue() - 1];
          if (myCurrentModifier != modifier) {
            myCurrentModifier = modifier;
            modifierChangedConsumer.accept(modifier);
            setText(getPresentableText(modifier));
            fireStateChanged();
          }
        });
        slider.setLabelTable(sliderLabels);
        slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
        slider.setPreferredSize(JBUI.size(150, modifiers.length * 25));
        slider.setPaintLabels(true);
        slider.setSnapToTicks(true);
        slider.setValue(ArrayUtil.find(modifiers, myCurrentModifier) + 1);
        final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(slider, null).setTitle("Effective Visibility").setCancelOnClickOutside(true).setMovable(true)
            .createPopup();
        popup.show(new RelativePoint(VisibilityModifierChooser.this, new Point(getWidth(), 0)));
        return true;
      }
    }.installOn(this);
  }

  private void fireStateChanged() {
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  private static String getPresentableText(String modifier) {
    return StringUtil.capitalize(VisibilityUtil.toPresentableText(modifier));
  }

  @Override
  public void setForeground(Color fg) {
    super.setForeground(isEnabled() ? JBCurrentTheme.Link.linkColor() : fg);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled && myCanBeEnabled.get());
  }

  @Override
  public void addChangeListener(ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(ChangeListener changeListener) {
    myListeners.remove(changeListener);
  }
}
