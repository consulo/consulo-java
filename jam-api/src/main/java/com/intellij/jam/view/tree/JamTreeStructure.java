/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.jam.view.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTreeStructure;

public class JamTreeStructure extends SimpleTreeStructure {
  private final SimpleNode myRootDescriptor;
  private final Project myProject;

  public JamTreeStructure(SimpleNode rootDescriptor, Project project) {
    myProject = project;
    myRootDescriptor = rootDescriptor;
  }

  public Project getProject() {
    return myProject;
  }

  public Object getRootElement() {
    return myRootDescriptor;
  }

  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  @Override
  public void commit() {
    // PSI event will trigger refresh later
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      }
    }, myProject.getDisposed());
  }

  public Object getParentElement(Object element) {
    if (element instanceof SimpleNode) {
      return super.getParentElement(element);
    }
    return null;
  }

  public boolean isAlwaysLeaf(Object element) {
    return element instanceof JamNodeDescriptor && ((JamNodeDescriptor)element).isAlwaysLeaf();
  }

  public static JamTreeStructure asyncInstance(SimpleNode rootDescriptor, Project project) {
    return new JamTreeStructure(rootDescriptor, project) {

      @Override
      public boolean isToBuildChildrenInBackground(final Object element) {
        return true;
      }
    };
  }

}
