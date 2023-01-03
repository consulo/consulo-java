/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 27, 2002
 * Time: 2:57:13 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInspection.ex;

import com.intellij.java.analysis.impl.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
@State(
  name = "JavaEntryPointsManager",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/misc.xml")
  })
@ServiceImpl
public class EntryPointsManagerImpl extends EntryPointsManagerBase implements PersistentStateComponent<Element> {
  @Inject
  public EntryPointsManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void configureAnnotations() {
    final List<String> list = new ArrayList<String>(ADDITIONAL_ANNOTATIONS);
    final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(list,
                                                                                        "Do not check if annotated by", true);
    new DialogWrapper(myProject) {
      {
        init();
        setTitle("Configure Annotations");
      }

      @Override
      protected JComponent createCenterPanel() {
        return listPanel;
      }

      @Override
      protected void doOKAction() {
        ADDITIONAL_ANNOTATIONS.clear();
        ADDITIONAL_ANNOTATIONS.addAll(list);
        DaemonCodeAnalyzer.getInstance(myProject).restart();
        super.doOKAction();
      }
    }.show();
  }

  @Override
  public Button createConfigureAnnotationsBtn() {
    return createConfigureAnnotationsButton();
  }

  @Nonnull
  public static Button createConfigureAnnotationsButton() {
    final Button configureAnnotations = Button.create(LocalizeValue.localizeTODO("Configure annotations..."));
    configureAnnotations.addClickListener(e -> getInstance(ProjectUtil.guessCurrentProject((JComponent)TargetAWT.to(configureAnnotations))).configureAnnotations());
    return configureAnnotations;
  }
}
