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
package com.intellij.java.language.util;

import consulo.language.editor.ui.TreeChooser;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * User: anna
 * Date: Jan 24, 2005
 */
public interface TreeClassChooser extends TreeChooser<PsiClass> {
  @RequiredUIAccess
  PsiClass getSelected();

  @RequiredUIAccess
  void select(final PsiClass aClass);

  @RequiredUIAccess
  void selectDirectory(final PsiDirectory directory);

  @RequiredUIAccess
  void showDialog();

  @RequiredUIAccess
  void showPopup();
}
