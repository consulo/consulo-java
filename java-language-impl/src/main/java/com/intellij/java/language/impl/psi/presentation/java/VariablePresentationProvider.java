/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.psi.PsiVariable;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.navigation.NavigationItem;
import consulo.ui.image.Image;

/**
 * @author yole
 */
public abstract class VariablePresentationProvider<T extends PsiVariable & NavigationItem> implements ItemPresentationProvider<T> {
  @Override
  public ItemPresentation getPresentation(final T variable) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        //return PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
        return variable.getName();
      }

      @Override
      public String getLocationString() {
        return "";
      }

      @Override
      public Image getIcon() {
        return IconDescriptorUpdaters.getIcon(variable, 0);
      }                                                                                                   
    };
  }
}
