/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerContextUtil;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.PositionUtil;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ArgumentValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.FieldDescriptor;
import com.intellij.java.debugger.impl.ui.tree.LocalVariableDescriptor;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.ClassNotPreparedException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;

@ExtensionImpl(order = "last")
public class DefaultSourcePositionProvider extends SourcePositionProvider {
  @Nullable
  @Override
  protected SourcePosition computeSourcePosition(@Nonnull NodeDescriptor descriptor,
                                                 @Nonnull Project project,
                                                 @Nonnull DebuggerContextImpl context,
                                                 boolean nearest) {
    StackFrameProxyImpl frame = context.getFrameProxy();
    if (frame == null) {
      return null;
    }

    if (descriptor instanceof FieldDescriptor) {
      return getSourcePositionForField((FieldDescriptor)descriptor, project, context, nearest);
    }
    else if (descriptor instanceof LocalVariableDescriptor) {
      return getSourcePositionForLocalVariable(descriptor.getName(), project, context, nearest);
    }
    else if (descriptor instanceof ArgumentValueDescriptorImpl) {
      Collection<String> names = ((ArgumentValueDescriptorImpl)descriptor).getVariable().getMatchedNames();
      if (!names.isEmpty()) {
        return getSourcePositionForLocalVariable(names.iterator().next(), project, context, nearest);
      }
    }
    else if (descriptor instanceof MethodReturnValueDescriptorImpl) {
      DebugProcessImpl debugProcess = context.getDebugProcess();
      if (debugProcess != null) {
        return debugProcess.getPositionManager().getSourcePosition(((MethodReturnValueDescriptorImpl)descriptor).getMethod().location());
      }
    }
    return null;
  }

  @jakarta.annotation.Nullable
  private SourcePosition getSourcePositionForField(@Nonnull FieldDescriptor descriptor,
                                                   @Nonnull Project project,
                                                   @Nonnull DebuggerContextImpl context,
                                                   boolean nearest) {
    final ReferenceType type = descriptor.getField().declaringType();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final String fieldName = descriptor.getField().name();
    if (fieldName.startsWith(FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
      // this field actually mirrors a local variable in the outer class
      String varName = fieldName.substring(fieldName.lastIndexOf('$') + 1);
      PsiElement element = PositionUtil.getContextElement(context);
      if (element == null) {
        return null;
      }
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
      if (aClass == null) {
        return null;
      }
      PsiElement navigationElement = aClass.getNavigationElement();
      if (!(navigationElement instanceof PsiClass)) {
        return null;
      }
      aClass = (PsiClass)navigationElement;
      PsiVariable psiVariable = facade.getResolveHelper().resolveReferencedVariable(varName, aClass);
      if (psiVariable == null) {
        return null;
      }
      if (nearest) {
        return DebuggerContextUtil.findNearest(context, psiVariable, aClass.getContainingFile());
      }
      return SourcePosition.createFromElement(psiVariable);
    }
    else {
      final DebuggerSession session = context.getDebuggerSession();
      final GlobalSearchScope scope = session != null ? session.getSearchScope() : GlobalSearchScope.allScope(project);
      PsiClass aClass = facade.findClass(type.name().replace('$', '.'), scope);
      if (aClass == null) {
        // trying to search, assuming declaring class is an anonymous class
        final DebugProcessImpl debugProcess = context.getDebugProcess();
        if (debugProcess != null) {
          try {
            final List<Location> locations = type.allLineLocations();
            if (!locations.isEmpty()) {
              // important: use the last location to be sure the position will be within the anonymous class
              final Location lastLocation = locations.get(locations.size() - 1);
              final SourcePosition position = debugProcess.getPositionManager().getSourcePosition(lastLocation);
              aClass = JVMNameUtil.getClassAt(position);
            }
          }
          catch (AbsentInformationException ignored) {
          }
          catch (ClassNotPreparedException ignored) {
          }
        }
      }

      if (aClass != null) {
        PsiField field = aClass.findFieldByName(fieldName, false);
        if (field == null) {
          return null;
        }
        if (nearest) {
          return DebuggerContextUtil.findNearest(context, field.getNavigationElement(), aClass.getContainingFile());
        }
        return SourcePosition.createFromElement(field);
      }
      return null;
    }
  }

  @jakarta.annotation.Nullable
  private SourcePosition getSourcePositionForLocalVariable(String name,
                                                           @Nonnull Project project,
                                                           @Nonnull DebuggerContextImpl context,
                                                           boolean nearest) {
    PsiElement place = PositionUtil.getContextElement(context);
    if (place == null) {
      return null;
    }

    PsiVariable psiVariable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(name, place);
    if (psiVariable == null) {
      return null;
    }

    PsiFile containingFile = psiVariable.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    if (nearest) {
      return DebuggerContextUtil.findNearest(context, psiVariable, containingFile);
    }
    return SourcePosition.createFromElement(psiVariable);
  }
}