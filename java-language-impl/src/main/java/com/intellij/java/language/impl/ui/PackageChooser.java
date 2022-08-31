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

package com.intellij.java.language.impl.ui;

import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import java.util.List;

/**
 * @author ven
 */
public abstract class PackageChooser extends DialogWrapper {
  public PackageChooser(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  public abstract PsiJavaPackage getSelectedPackage();

  public abstract List<PsiJavaPackage> getSelectedPackages();

  public abstract void selectPackage(String qualifiedName);
}
