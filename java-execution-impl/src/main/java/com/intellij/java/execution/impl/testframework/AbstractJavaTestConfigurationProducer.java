/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.testframework;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.junit.JavaRunConfigurationProducerBase;
import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.test.TestsUIUtil;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import java.util.*;

public abstract class AbstractJavaTestConfigurationProducer<T extends JavaTestConfigurationBase> extends JavaRunConfigurationProducerBase<T> {
  protected AbstractJavaTestConfigurationProducer(ConfigurationFactory configurationFactory) {
    super(configurationFactory);
  }

  protected AbstractJavaTestConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }


  protected boolean isTestClass(PsiClass psiClass) {
    if (psiClass != null) {
      JavaTestFramework framework = getCurrentFramework(psiClass);
      return framework != null && framework.isTestClass(psiClass);
    }
    return false;
  }

  protected boolean isTestMethod(boolean checkAbstract, PsiMethod method) {
    JavaTestFramework framework = getCurrentFramework(method.getContainingClass());
    return framework != null && framework.isTestMethod(method, checkAbstract);
  }

  protected JavaTestFramework getCurrentFramework(PsiClass psiClass) {
    if (psiClass != null) {
      ConfigurationType configurationType = getConfigurationType();
      Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(psiClass);
      return frameworks.stream()
        .filter(
          framework -> framework instanceof JavaTestFramework javaTestFramework
            && javaTestFramework.isMyConfigurationType(configurationType)
        )
        .map(framework -> (JavaTestFramework) framework)
        .findFirst()
        .orElse(null);
    }
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(T configuration, ConfigurationContext context) {
    if (isMultipleElementsSelected(context)) {
      return false;
    }
    final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(getConfigurationType());
    final Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();

    RunnerAndConfigurationSettings template =
      RunManager.getInstance(location.getProject()).getConfigurationTemplate(getConfigurationFactory());
    @SuppressWarnings("unchecked")
    final Module predefinedModule = ((T) template.getConfiguration()).getConfigurationModule().getModule();
    final String vmParameters = predefinedConfiguration instanceof CommonJavaRunConfigurationParameters runConfigurationParameters
      ? runConfigurationParameters.getVMParameters() : null;
    if (vmParameters != null && !Comparing.strEqual(vmParameters, configuration.getVMParameters())) {
      return false;
    }
    if (differentParamSet(configuration, contextLocation)) {
      return false;
    }

    if (configuration.isConfiguredByElement(element)) {
      final Module configurationModule = configuration.getConfigurationModule().getModule();
      if (Comparing.equal(location.getModule(), configurationModule)) {
        return true;
      }
      if (Comparing.equal(predefinedModule, configurationModule)) {
        return true;
      }
    }

    return false;
  }

  protected boolean differentParamSet(T configuration, Location contextLocation) {
    String paramSetName = contextLocation instanceof PsiMemberParameterizedLocation memberParameterizedLocation
      ? configuration.prepareParameterizedParameter(memberParameterizedLocation.getParamSetName()) : null;
    return paramSetName != null && !Comparing.strEqual(paramSetName, configuration.getProgramParameters());
  }

  public Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), this::isTestClass);
  }

  public void collectTestMembers(PsiElement[] psiElements, boolean checkAbstract, boolean checkIsTest, PsiElementProcessor.CollectElements<PsiElement> collectingProcessor) {
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner classOwner) {
        final PsiClass[] classes = classOwner.getClasses();
        for (PsiClass aClass : classes) {
          if ((!checkIsTest && aClass.hasModifierProperty(PsiModifier.PUBLIC) || checkIsTest && isTestClass(aClass))
            && !collectingProcessor.execute(aClass)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiClass psiClass) {
        if ((!checkIsTest && psiClass.hasModifierProperty(PsiModifier.PUBLIC) || checkIsTest && isTestClass(psiClass))
          && !collectingProcessor.execute(psiElement)) {
          return;
        }
      } else if (psiElement instanceof PsiMethod method) {
        if (checkIsTest && isTestMethod(checkAbstract, method) && !collectingProcessor.execute(psiElement)) {
          return;
        }
        if (!checkIsTest) {
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.PUBLIC) && !collectingProcessor.execute(psiElement)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiDirectory directory) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null && !collectingProcessor.execute(aPackage)) {
          return;
        }
      }
    }
  }

  @RequiredReadAction
  protected boolean collectContextElements(
    DataContext dataContext,
    boolean checkAbstract,
    boolean checkIsTest,
    LinkedHashSet<String> classes,
    PsiElementProcessor.CollectElements<PsiElement> processor
  ) {
    PsiElement[] elements = dataContext.getData(PsiElement.KEY_OF_ARRAY);
    if (elements != null) {
      return collectTestMembers(elements, checkAbstract, checkIsTest, processor, classes);
    } else {
      final Editor editor = dataContext.getData(Editor.KEY);
      PsiElement element = null;
      if (editor != null) {
        final PsiFile editorFile = dataContext.getData(PsiFile.KEY);
        final List<Caret> allCarets = editor.getCaretModel().getAllCarets();
        if (editorFile != null) {
          if (allCarets.size() > 1) {
            final Set<PsiMethod> methods = new LinkedHashSet<>();
            for (Caret caret : allCarets) {
              ContainerUtil.addIfNotNull(methods, PsiTreeUtil.getParentOfType(editorFile.findElementAt(caret.getOffset()), PsiMethod.class));
            }
            if (!methods.isEmpty()) {
              return collectTestMembers(methods.toArray(PsiElement.EMPTY_ARRAY), checkAbstract, checkIsTest, processor, classes);
            }
          } else {
            element = editorFile.findElementAt(editor.getCaretModel().getOffset());

            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasSelection()) {
              int selectionStart = selectionModel.getSelectionStart();
              PsiClass psiClass = PsiTreeUtil.getParentOfType(editorFile.findElementAt(selectionStart), PsiClass.class);
              if (psiClass != null) {
                TextRange selectionRange = new TextRange(selectionStart, selectionModel.getSelectionEnd());
                PsiMethod[] methodsInSelection = Arrays.stream(psiClass.getMethods()).filter(method ->
                {
                  TextRange methodTextRange = method.getTextRange();
                  return methodTextRange != null && selectionRange.contains(methodTextRange);
                }).toArray(PsiMethod[]::new);
                if (methodsInSelection.length > 0) {
                  return collectTestMembers(methodsInSelection, checkAbstract, checkIsTest, processor, classes);
                }
              }
            }
          }
        }
      }

      if (element == null) {
        element = dataContext.getData(PsiElement.KEY);
      }

      final VirtualFile[] files = dataContext.getData(VirtualFile.KEY_OF_ARRAY);
      if (files != null) {
        Project project = dataContext.getData(Project.KEY);
        if (project != null) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          for (VirtualFile file : files) {
            final PsiFile psiFile = psiManager.findFile(file);
            if (psiFile instanceof PsiClassOwner classOwner) {
              PsiClass[] psiClasses = classOwner.getClasses();
              if (element != null && psiClasses.length > 0) {
                for (PsiClass aClass : psiClasses) {
                  if (PsiTreeUtil.isAncestor(aClass, element, false)) {
                    psiClasses = new PsiClass[]{aClass};
                    break;
                  }
                }
              }
              collectTestMembers(psiClasses, checkAbstract, checkIsTest, processor);
              for (PsiElement psiMember : processor.getCollection()) {
                classes.add(((PsiClass) psiMember).getQualifiedName());
              }
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  private boolean collectTestMembers(PsiElement[] elements, boolean checkAbstract, boolean checkIsTest, PsiElementProcessor.CollectElements<PsiElement> processor, LinkedHashSet<String> classes) {
    collectTestMembers(elements, checkAbstract, checkIsTest, processor);
    for (PsiElement psiClass : processor.getCollection()) {
      classes.add(getQName(psiClass));
    }
    return classes.size() > 1;
  }

  protected PsiElement[] collectLocationElements(LinkedHashSet<String> classes, DataContext dataContext) {
    final Location<?>[] locations = dataContext.getData(Location.DATA_KEYS);
    if (locations != null) {
      List<PsiElement> elements = new ArrayList<>();
      for (Location<?> location : locations) {
        final PsiElement psiElement = location.getPsiElement();
        if (psiElement instanceof PsiNamedElement) {
          String qName = getQName(psiElement, location);
          if (qName != null) {
            classes.add(qName);
            elements.add(psiElement);
          }
        }
      }
      return elements.toArray(new PsiElement[elements.size()]);
    }
    return null;
  }

  public String getQName(PsiElement psiMember) {
    return getQName(psiMember, null);
  }

  public String getQName(PsiElement psiMember, Location location) {
    if (psiMember instanceof PsiClass psiClass) {
      return ClassUtil.getJVMClassName(psiClass);
    } else if (psiMember instanceof PsiMember member) {
      final PsiClass containingClass = location instanceof MethodLocation methodLocation ? methodLocation.getContainingClass()
        : location instanceof PsiMemberParameterizedLocation memberParameterizedLocation ? memberParameterizedLocation.getContainingClass()
        : member.getContainingClass();
      assert containingClass != null;
      return ClassUtil.getJVMClassName(containingClass) + "," + getMethodPresentation((PsiMember) psiMember);
    } else if (psiMember instanceof PsiPackage psiPackage) {
      return psiPackage.getQualifiedName() + ".*";
    }
    return null;
  }

  protected String getMethodPresentation(PsiMember psiMember) {
    return psiMember.getName();
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
    if (!context.containsMultipleSelection()) {
      return false;
    }
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return false;
    }
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor = new PsiElementProcessor.CollectElementsWithLimit<>(2);
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    if (locationElements != null) {
      collectTestMembers(locationElements, false, false, processor);
    } else {
      collectContextElements(dataContext, false, false, classes, processor);
    }
    return processor.getCollection().size() > 1;
  }

  public void setupConfigurationParamName(T configuration, Location contextLocation) {
    if (contextLocation instanceof PsiMemberParameterizedLocation memberParameterizedLocation) {
      final String paramSetName = memberParameterizedLocation.getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(configuration.prepareParameterizedParameter(paramSetName));
      }
    }
  }
}
