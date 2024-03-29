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
package com.intellij.java.execution.impl.stacktrace;

import consulo.execution.action.Location;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;

public class MethodLineLocation extends MethodLocation {
  private final int myLineNumber;

  public MethodLineLocation(final Project project, final PsiMethod method, final Location<PsiClass> classLocation, final int lineNumber) {
    super(project, method, classLocation);
    myLineNumber = lineNumber;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    final VirtualFile virtualFile = getContainingClass().getContainingFile().getVirtualFile();
    return OpenFileDescriptorFactory.getInstance(getProject()).builder(virtualFile).line(myLineNumber).build();
  }
}
