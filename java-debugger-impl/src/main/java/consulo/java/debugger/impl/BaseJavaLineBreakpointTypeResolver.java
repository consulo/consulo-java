package consulo.java.debugger.impl;

import com.intellij.java.debugger.impl.ui.breakpoints.JavaFieldBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaLineBreakpointTypeBase;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaMethodBreakpointType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.function.Processor;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.execution.debug.breakpoint.XLineBreakpointTypeResolver;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 10/12/2022
 */
public abstract class BaseJavaLineBreakpointTypeResolver implements XLineBreakpointTypeResolver {
  @Nullable
  @RequiredReadAction
  @Override
  public XLineBreakpointType<?> resolveBreakpointType(@Nonnull Project project, @Nonnull VirtualFile virtualFile, final int line) {
    PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null) {
      return null;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assert document != null;
    final Ref<JavaLineBreakpointTypeBase> result = Ref.create();
    XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
      @Override
      @RequiredReadAction
      public boolean process(PsiElement element) {
        // avoid comments
        if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element,
                                                                               PsiComment.class,
                                                                               PsiImportStatementBase.class,
                                                                               PsiPackageStatement.class) != null)) {
          return true;
        }
        PsiElement parent = element;
        while (element != null) {
          // skip modifiers
          if (element instanceof PsiModifierList) {
            element = element.getParent();
            continue;
          }

          final int offset = element.getTextOffset();
          if (offset >= 0) {
            if (document.getLineNumber(offset) != line) {
              break;
            }
          }
          parent = element;
          element = element.getParent();
        }

        if (parent instanceof PsiMethod) {
          if (parent.getTextRange().getEndOffset() >= document.getLineEndOffset(line)) {
            PsiCodeBlock body = ((PsiMethod)parent).getBody();
            if (body != null) {
              PsiStatement[] statements = body.getStatements();
              if (statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == line) {
                result.set(JavaLineBreakpointType.getInstance());
              }
            }
          }
          if (result.isNull()) {
            result.set(JavaMethodBreakpointType.getInstance());
          }
        }
        else if (parent instanceof PsiField) {
          if (result.isNull()) {
            result.set(JavaFieldBreakpointType.getInstance());
          }
        }
        else {
          result.set(JavaLineBreakpointType.getInstance());
        }
        return true;
      }
    });
    return result.get();
  }
}
