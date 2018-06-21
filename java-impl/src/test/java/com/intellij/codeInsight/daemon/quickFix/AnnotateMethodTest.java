package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;

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
          public int shouldAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
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
