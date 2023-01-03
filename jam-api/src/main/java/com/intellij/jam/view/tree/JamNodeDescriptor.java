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

import consulo.dataContext.DataManager;
import consulo.language.editor.PlatformDataKeys;
import consulo.ui.ex.DeleteProvider;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.awt.tree.ValidateableNode;
import com.intellij.jam.JamMessages;
import consulo.dataContext.DataProvider;
import consulo.project.Project;
import consulo.ui.color.ColorValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.ui.ex.awt.tree.SimpleNode;
import consulo.ui.ex.OpenSourceUtil;
import consulo.ui.image.Image;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.event.InputEvent;

/**
 * author: lesya
 */
public abstract class JamNodeDescriptor<P> extends SimpleNode implements DataProvider, ValidateableNode {
  public static final JamNodeDescriptor[] EMPTY_ARRAY = new JamNodeDescriptor[0];

  private final Object myParameters;
  private final P myElement;
  private String myTooltip;

  protected JamNodeDescriptor(Project project, NodeDescriptor parentDescriptor, Object parameters, P element) {
    super(project, parentDescriptor);
    myParameters = parameters;
    myElement = element;
  }

  @Nonnull
  public Object[] getEqualityObjects() {
    return new Object[] { myElement };
  }

  protected void doUpdate() {
    setIcon(getNewIcon());
    final String nodeText = getNewNodeText();
    setNodeText(StringUtil.isNotEmpty(nodeText) ? nodeText : JamMessages.message("unnamed.element.presentable.name"), null, !isValid());
    myTooltip = getNewTooltip();
  }

  protected P updateElement() {
    return myElement;
  }

  @Nullable
  protected abstract String getNewNodeText();

  @Nullable
  protected Image getNewIcon() {
    return getIcon();
  }

  protected ColorValue getNewColor() {
    return myColor;
  }

  public SimpleNode[] getChildren(){
    return EMPTY_ARRAY;
  }

  @Nullable
  public Object getData(String dataId) {
    return null;
  }

  public String getTooltip() {
    return myTooltip;
  }

  @Nullable
  public String getNewTooltip() {
    return getNewNodeText();
  }

  public int getWeight() {
    return 0;
  }

  @Nullable
  protected DeleteProvider getDeleteProvider() {
    return null;
  }

  protected Object getParameters() {
    return myParameters;
  }

  @Nullable
  public final Object getDataForElement(Key<?> dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER == dataId) {
      return getDeleteProvider();
    }
    return getData(dataId);
  }

  public final P getElement() {
    return myElement;
  }

  @Nullable
  public String getComment() {
    return null;
  }

  public boolean isValid() {
    return true;
  }

  @Override
  public void handleDoubleClickOrEnter(final SimpleTree tree, final InputEvent inputEvent) {
    OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(tree), false);
  }

  public boolean isAlwaysLeaf() {
    return false;
  }
}
