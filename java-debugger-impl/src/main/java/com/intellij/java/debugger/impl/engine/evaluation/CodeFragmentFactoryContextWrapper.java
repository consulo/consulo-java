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
package com.intellij.java.debugger.impl.engine.evaluation;

import com.intellij.java.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiLocalVariable;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 30, 2010
 */
@SuppressWarnings("ExtensionImplIsNotAnnotatedInspection")
public class CodeFragmentFactoryContextWrapper extends CodeFragmentFactory {
  public static final Key<Value> LABEL_VARIABLE_VALUE_KEY = Key.create("_label_variable_value_key_");
  public static final String DEBUG_LABEL_SUFFIX = "_DebugLabel";

  private final CodeFragmentFactory myDelegate;

  public CodeFragmentFactoryContextWrapper(CodeFragmentFactory delegate) {
    myDelegate = delegate;
  }

  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return myDelegate.createCodeFragment(item, wrapContext(project, context), project);
  }

  public JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return myDelegate.createPresentationCodeFragment(item, wrapContext(project, context), project);
  }

  public boolean isContextAccepted(PsiElement contextElement) {
    return myDelegate.isContextAccepted(contextElement);
  }

  public LanguageFileType getFileType() {
    return myDelegate.getFileType();
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return myDelegate.getEvaluatorBuilder();
  }

  private PsiElement wrapContext(Project project, final PsiElement originalContext) {
    if (project.isDefault()) {
      return originalContext;
    }
    PsiElement context = originalContext;
    final DebugProcessImpl process = DebuggerManagerEx.getInstanceEx(project).getContext().getDebugProcess();
    if (process != null) {
      final Map<ObjectReference, ValueMarkup> markupMap = ValueDescriptorImpl.getMarkupMap(process);
      if (markupMap != null && markupMap.size() > 0) {
        final Pair<String, Map<String, ObjectReference>> markupVariables = createMarkupVariablesText(markupMap);
        int offset = markupVariables.getFirst().length() - 1;
        final TextWithImportsImpl textWithImports = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, markupVariables.getFirst(), "", myDelegate.getFileType());
        final JavaCodeFragment codeFragment = myDelegate.createCodeFragment(textWithImports, context, project);
        codeFragment.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitLocalVariable(PsiLocalVariable variable) {
            final String name = variable.getName();
            variable.putUserData(LABEL_VARIABLE_VALUE_KEY, markupVariables.getSecond().get(name));
          }
        });
        final PsiElement newContext = codeFragment.findElementAt(offset);
        if (newContext != null) {
          context = newContext;
        }
      }
    }
    return context;
  }

  private static Pair<String, Map<String, ObjectReference>> createMarkupVariablesText(Map<ObjectReference, ValueMarkup> markupMap) {
    final Map<String, ObjectReference> reverseMap = new HashMap<>();
    final StringBuilder buffer = new StringBuilder();
    for (Iterator<Map.Entry<ObjectReference, ValueMarkup>> it = markupMap.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<ObjectReference, ValueMarkup> entry = it.next();
      final ObjectReference objectRef = entry.getKey();
      final ValueMarkup markup = entry.getValue();
      String labelName = markup.getText();
      if (!StringUtil.isJavaIdentifier(labelName)) {
        continue;
      }
      try {
        final String typeName = objectRef.type().name();
        labelName += DEBUG_LABEL_SUFFIX;
        if (buffer.length() > 0) {
          buffer.append("\n");
        }
        buffer.append(typeName).append(" ").append(labelName).append(";");
        reverseMap.put(labelName, objectRef);
      } catch (ObjectCollectedException e) {
        it.remove();
      }
    }
    buffer.append(" ");
    return Pair.create(buffer.toString(), reverseMap);
  }
}
