package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.analysis.impl.codeInspection.AnnotateMethodFix;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.nullable.NullableStuffInspection;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiMethod;

public abstract class AnnotateMethodTest extends LightQuickFix15TestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethod";
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new NullableStuffInspection(){
     // @Override
      protected AnnotateMethodFix createAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
        return new AnnotateMethodFix(defaultNotNull, annotationsToRemove){
         // @Override
          public int shouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod, Project project) {
            @NonNls String name = method.getName();
            int ret = name.startsWith("annotateBase") ? 0  // yes, annotate all
                                                      : name.startsWith("dontAnnotateBase") ? 1 // do not annotate base
                                                                                            : 2; //abort
            myMustBeAvailableAfterInvoke = ret == 2;
            return ret;
          }
        };
      }

    }};
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }

  public void test() throws Exception { doAllTests(); }
}
