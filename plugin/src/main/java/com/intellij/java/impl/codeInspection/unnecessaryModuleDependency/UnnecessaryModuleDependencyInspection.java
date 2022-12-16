package com.intellij.java.impl.codeInspection.unnecessaryModuleDependency;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.scope.AnalysisScope;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.ModuleOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
@ExtensionImpl
public class UnnecessaryModuleDependencyInspection extends GlobalInspectionTool {

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return new UnnecessaryModuleDependencyAnnotator(refManager);
  }

  @Override
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                AnalysisScope scope,
                                                InspectionManager manager,
                                                final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule) {
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
      final Module[] declaredDependencies = ModuleRootManager.getInstance(module).getDependencies();
      List<CommonProblemDescriptor> descriptors = new ArrayList<CommonProblemDescriptor>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      for (final Module dependency : declaredDependencies) {
        if (modules == null || !modules.contains(dependency)) {
          final CommonProblemDescriptor problemDescriptor;
          if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
            problemDescriptor = manager.createProblemDescriptor(
              InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", module.getName(), dependency.getName()),
              new RemoveModuleDependencyFix(module, dependency));
          }
          else {
            String message = InspectionsBundle
              .message("suspected.module.dependency.problem.descriptor", module.getName(), dependency.getName(), scope.getDisplayName(),
                       dependency.getName());
            problemDescriptor = manager.createProblemDescriptor(message);
          }
          descriptors.add(problemDescriptor);
        }
      }
      return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
    }
    return null;
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("unnecessary.module.dependency.display.name");
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return "UnnecessaryModuleDependencyInspection";
  }

  public static class RemoveModuleDependencyFix implements QuickFix {
    private final Module myModule;
    private final Module myDependency;

    public RemoveModuleDependencyFix(Module module, Module dependency) {
      myModule = module;
      myDependency = dependency;
    }

    @Override
    @Nonnull
    public String getName() {
      return "Remove dependency";
    }

    @Override
    @Nonnull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull CommonProblemDescriptor descriptor) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final Module mDependency = ((ModuleOrderEntry)entry).getModule();
          if (Comparing.equal(mDependency, myDependency)) {
            model.removeOrderEntry(entry);
            break;
          }
        }
      }
      model.commit();
    }
  }
}
