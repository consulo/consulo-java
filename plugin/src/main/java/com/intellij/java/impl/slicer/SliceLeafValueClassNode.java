/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.slicer;

import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * User: cdr
 */
public class SliceLeafValueClassNode extends SliceLeafValueRootNode {
  private final String myClassName;

  public SliceLeafValueClassNode(@Nonnull Project project, SliceNode root, String className) {
    super(project, root.getValue().getElement(), root, new ArrayList<SliceNode>(), root.getValue().params);
    myClassName = className;
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void customizeCellRenderer(SliceUsageCellRenderer renderer,
                                    JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    renderer.append(myClassName, SimpleTextAttributes.DARK_TEXT);
  }

  @Override
  public String toString() {
    return myClassName;
  }
}
