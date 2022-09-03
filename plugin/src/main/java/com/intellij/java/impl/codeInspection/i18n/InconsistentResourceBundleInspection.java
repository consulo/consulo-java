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
package com.intellij.java.impl.codeInspection.i18n;

import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.ide.impl.idea.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiFile;
import consulo.util.collection.BidirectionalMap;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class InconsistentResourceBundleInspection extends GlobalSimpleInspectionTool {
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_MISSING_TRANSLATIONS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_INCONSISTENT_PROPERTIES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_DUPLICATED_PROPERTIES = true;

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inconsistent.resource.bundle.display.name");
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "InconsistentResourceBundle";
  }

  @Override
  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inconsistent.bundle.report.inconsistent.properties"), "REPORT_INCONSISTENT_PROPERTIES");
    panel.addCheckbox(InspectionsBundle.message("inconsistent.bundle.report.missing.translations"), "REPORT_MISSING_TRANSLATIONS");
    panel.addCheckbox(InspectionsBundle.message("inconsistent.bundle.report.duplicate.properties.values"), "REPORT_DUPLICATED_PROPERTIES");
    return panel;
  }


  private static final Key<Set<ResourceBundle>> VISITED_BUNDLES_KEY = Key.create("VISITED_BUNDLES_KEY");
  @Override
  public void inspectionStarted(@Nonnull InspectionManager manager,
                                @Nonnull GlobalInspectionContext globalContext,
                                @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.putUserData(VISITED_BUNDLES_KEY, new HashSet<ResourceBundle>());
  }

  @Override
  public void checkFile(@Nonnull PsiFile file,
                        @Nonnull InspectionManager manager,
                        @Nonnull ProblemsHolder problemsHolder,
                        @Nonnull GlobalInspectionContext globalContext,
                        @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    Set<ResourceBundle> visitedBundles = globalContext.getUserData(VISITED_BUNDLES_KEY);
    checkFile(file, manager, visitedBundles, globalContext.getRefManager(), problemDescriptionsProcessor);
  }

  private void checkFile(@Nonnull final PsiFile file,
                         @Nonnull final InspectionManager manager,
                         @Nonnull Set<ResourceBundle> visitedBundles,
                         RefManager refManager, ProblemDescriptionsProcessor processor) {
    if (!(file instanceof PropertiesFile)) return;
    final PropertiesFile propertiesFile = (PropertiesFile)file;
    ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    if (!visitedBundles.add(resourceBundle)) return;
    List<PropertiesFile> files = resourceBundle.getPropertiesFiles(manager.getProject());
    if (files.size() < 2) return;
    BidirectionalMap<PropertiesFile, PropertiesFile> parents = new BidirectionalMap<PropertiesFile, PropertiesFile>();
    for (PropertiesFile f : files) {
      PropertiesFile parent = PropertiesUtil.getParent(f, files);
      if (parent != null) {
        parents.put(f, parent);
      }
    }
    Map<PropertiesFile, Set<String>> keysUpToParent = new HashMap<PropertiesFile, Set<String>>();
    for (PropertiesFile f : files) {
      Set<String> keys = new HashSet<String>(f.getNamesMap().keySet());
      PropertiesFile parent = parents.get(f);
      while (parent != null) {
        keys.addAll(parent.getNamesMap().keySet());
        parent = parents.get(parent);
      }
      keysUpToParent.put(f, keys);
    }
    if (REPORT_MISSING_TRANSLATIONS) {
      checkMissingTranslations(parents, files, keysUpToParent, manager, refManager, processor);
    }
    if (REPORT_INCONSISTENT_PROPERTIES) {
      checkConsistency(parents, files, keysUpToParent, manager, refManager, processor);
    }
    if (REPORT_DUPLICATED_PROPERTIES) {
      checkDuplicatedProperties(parents, files, keysUpToParent, manager, refManager, processor);
    }
  }

  private static void checkDuplicatedProperties(final BidirectionalMap<PropertiesFile, PropertiesFile> parents,
                                                final List<PropertiesFile> files,
                                                final Map<PropertiesFile, Set<String>> keysUpToParent,
                                                final InspectionManager manager,
                                                RefManager refManager,
                                                ProblemDescriptionsProcessor processor) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      if (parent == null) continue;
      Set<String> parentKeys = keysUpToParent.get(parent);
      Set<String> overriddenKeys = new HashSet<String>(file.getNamesMap().keySet());
      overriddenKeys.retainAll(parentKeys);
      for (String overriddenKey : overriddenKeys) {
        IProperty property = file.findPropertyByKey(overriddenKey);
        assert property != null;
        while (parent != null) {
          IProperty parentProperty = parent.findPropertyByKey(overriddenKey);
          if (parentProperty != null && Comparing.strEqual(property.getValue(), parentProperty.getValue())) {
            String message = InspectionsBundle.message("inconsistent.bundle.property.inherited.with.the.same.value", parent.getName());
            ProblemDescriptor descriptor = manager.createProblemDescriptor(property.getPsiElement(), message,
                                                                           RemovePropertyLocalFix.INSTANCE,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
          }
          parent = parents.get(parent);
        }
      }
    }
  }

  private static void checkConsistency(final BidirectionalMap<PropertiesFile, PropertiesFile> parents, final List<PropertiesFile> files,
                                       final Map<PropertiesFile, Set<String>> keysUpToParent,
                                       final InspectionManager manager,
                                       RefManager refManager, ProblemDescriptionsProcessor processor) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      Set<String> parentKeys = keysUpToParent.get(parent);
      if (parent == null) {
        parentKeys = new HashSet<String>();
        for (PropertiesFile otherTopLevelFile : files) {
          if (otherTopLevelFile != file && parents.get(otherTopLevelFile) == null) {
            parent = otherTopLevelFile;
            parentKeys.addAll(otherTopLevelFile.getNamesMap().keySet());
          }
        }
        if (parent == null) continue;
      }
      Set<String> keys = new HashSet<String>(file.getNamesMap().keySet());
      keys.removeAll(parentKeys);
      for (String inconsistentKey : keys) {
        IProperty property = file.findPropertyByKey(inconsistentKey);
        assert property != null;
        String message = InspectionsBundle.message("inconsistent.bundle.property.error", inconsistentKey, parent.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(property.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
      }
    }
  }

  private static void checkMissingTranslations(final BidirectionalMap<PropertiesFile, PropertiesFile> parents,
                                               final List<PropertiesFile> files,
                                               final Map<PropertiesFile, Set<String>> keysUpToParent,
                                               final InspectionManager manager,
                                               RefManager refManager,
                                               ProblemDescriptionsProcessor processor) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      if (parent == null) continue;
      List<PropertiesFile> children = parents.getKeysByValue(file);
      boolean isLeaf = children == null || children.isEmpty();
      if (!isLeaf) continue;
      Set<String> keys = file.getNamesMap().keySet();
      Set<String> parentKeys = new HashSet<String>(keysUpToParent.get(parent));
      if (parent.getLocale().getLanguage().equals(file.getLocale().getLanguage())) {
        // properties can be left untranslated in the dialect files
        keys = new HashSet<String>(keys);
        keys.addAll(parent.getNamesMap().keySet());
        parent = parents.get(parent);
        if (parent == null) continue;
        parentKeys = new HashSet<String>(keysUpToParent.get(parent));
      }
      parentKeys.removeAll(keys);
      for (String untranslatedKey : parentKeys) {
        IProperty untranslatedProperty = null;
        PropertiesFile untranslatedFile = parent;
        while (untranslatedFile != null) {
          untranslatedProperty = untranslatedFile.findPropertyByKey(untranslatedKey);
          if (untranslatedProperty != null) break;
          untranslatedFile = parents.get(untranslatedFile);
        }
        assert untranslatedProperty != null;
        String message = InspectionsBundle.message("inconsistent.bundle.untranslated.property.error", untranslatedKey, file.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(untranslatedProperty.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(untranslatedFile.getContainingFile()), descriptor);
      }
    }
  }
}
