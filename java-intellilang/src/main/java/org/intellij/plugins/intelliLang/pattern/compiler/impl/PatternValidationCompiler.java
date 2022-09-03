/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.pattern.compiler.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.pattern.compiler.AnnotationBasedInstrumentingCompiler;
import org.intellij.plugins.intelliLang.pattern.compiler.Instrumenter;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import consulo.internal.org.objectweb.asm.ClassWriter;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import consulo.project.Project;
import consulo.util.lang.Pair;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.indexing.search.searches.AnnotatedMembersSearch;
import consulo.util.collection.ArrayUtil;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;

public class PatternValidationCompiler extends AnnotationBasedInstrumentingCompiler {

  private final Map<String, String> myAnnotations = new HashMap<String, String>();

  protected String[] getAnnotationNames(Project project) {
    synchronized (myAnnotations) {
      myAnnotations.clear();
      final Pair<String, ? extends Set<String>> patternAnnotation =
        Configuration.getProjectInstance(project).getAdvancedConfiguration().getPatternAnnotationPair();

      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(patternAnnotation.first, scope);

      if (psiClass == null) {
        // annotation is not present in project's classpath, nothing to instrument
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }

      final Query<PsiMember> query = AnnotatedMembersSearch.search(psiClass, GlobalSearchScope.allScope(project));

      query.forEach(new Processor<PsiMember>() {
        public boolean process(PsiMember psiMember) {
          if (psiMember instanceof PsiClass) {
            final PsiClass clazz = (PsiClass)psiMember;
            if (clazz.isAnnotationType()) {
              final PsiAnnotation annotation = AnnotationUtil.findAnnotation(clazz, patternAnnotation.second);
              if (annotation != null) {
                final String s = AnnotationUtilEx.calcAnnotationValue(annotation, "value");
                if (s != null) {
                  myAnnotations.put(clazz.getQualifiedName(), s);
                }
              }
            }
          }
          return true;
        }
      });

      myAnnotations.put(patternAnnotation.first, null);

      final Set<String> names = myAnnotations.keySet();
      return ArrayUtil.toStringArray(names);
    }
  }

  protected boolean isEnabled() {
    final Configuration.InstrumentationType option = Configuration.getInstance().getAdvancedConfiguration().getInstrumentation();
    return option == Configuration.InstrumentationType.ASSERT || option == Configuration.InstrumentationType.EXCEPTION;
  }

  @Nonnull
  protected Instrumenter createInstrumenter(ClassWriter classwriter) {
    synchronized (myAnnotations) {
      final Configuration.InstrumentationType instrumentation = Configuration.getInstance().getAdvancedConfiguration().getInstrumentation();
      return new PatternValidationInstrumenter(new HashMap<String, String>(myAnnotations), classwriter, instrumentation);
    }
  }

  protected String getProgressMessage() {
    return "Inserting @Pattern assertions";
  }

  @Nonnull
  public String getDescription() {
    return "Pattern Validation";
  }
}
