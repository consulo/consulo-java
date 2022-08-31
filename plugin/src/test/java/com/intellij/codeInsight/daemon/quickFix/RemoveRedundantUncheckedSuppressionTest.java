/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.PossibleHeapPollutionVarargsInspection;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;


public abstract class RemoveRedundantUncheckedSuppressionTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    final PossibleHeapPollutionVarargsInspection varargsInspection = new PossibleHeapPollutionVarargsInspection();
    final UncheckedWarningLocalInspection warningLocalInspection = new UncheckedWarningLocalInspection();
    final RedundantSuppressInspection inspection = new RedundantSuppressInspection(){
      @Override
      protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @Nonnull InspectionManager manager) {
        return new InspectionToolWrapper[]{
          new LocalInspectionToolWrapper(varargsInspection),
          new LocalInspectionToolWrapper(warningLocalInspection)
        };
      }
    };

    return new LocalInspectionTool[] {
      new LocalInspectionTool() {
        @Nls
        @Nonnull
        @Override
        public String getGroupDisplayName() {
          return inspection.getGroupDisplayName();
        }

        @Nls
        @Nonnull
        @Override
        public String getDisplayName() {
          return inspection.getDisplayName();
        }

        @Nonnull
        @Override
        public String getShortName() {
          return inspection.getShortName();
        }

        @Nonnull
        @Override
        public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder,
                                              boolean isOnTheFly,
                                              @Nonnull LocalInspectionToolSession session) {
          return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {
              checkMember(aClass, inspection, holder);
            }

            @Override
            public void visitMethod(PsiMethod method) {
              checkMember(method, inspection, holder);
            }
          };
        }

        private void checkMember(PsiMember member, RedundantSuppressInspection inspection, ProblemsHolder holder) {
          final ProblemDescriptor[] problemDescriptors =
            (ProblemDescriptor[])inspection.checkElement(member, InspectionManager.getInstance(getProject()), getProject());
          if (problemDescriptors != null) {
            for (ProblemDescriptor problemDescriptor : problemDescriptors) {
              holder.registerProblem(problemDescriptor);
            }
          }
        }
      },
      varargsInspection,
      warningLocalInspection
    };
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantUncheckedVarargs";
  }

}
