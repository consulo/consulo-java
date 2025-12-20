/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExceptionUtils {

  private ExceptionUtils() {
  }

  private static final Set<String> s_genericExceptionTypes = new HashSet<String>(4);

  static {
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_THROWABLE);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_EXCEPTION);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION);
    s_genericExceptionTypes.add(CommonClassNames.JAVA_LANG_ERROR);
  }

  @Nonnull
  public static Set<PsiClassType> calculateExceptionsThrown(@Nonnull PsiElement element) {
    ExceptionsThrownVisitor visitor = new ExceptionsThrownVisitor();
    element.accept(visitor);
    return visitor.getExceptionsThrown();
  }

  public static boolean isGenericExceptionClass(@Nullable PsiType exceptionType) {
    if (!(exceptionType instanceof PsiClassType)) {
      return false;
    }
    PsiClassType classType = (PsiClassType) exceptionType;
    String className = classType.getCanonicalText();
    return s_genericExceptionTypes.contains(className);
  }

  public static boolean statementThrowsException(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiReturnStatement ||
        statement instanceof PsiExpressionStatement ||
        statement instanceof PsiExpressionListStatement ||
        statement instanceof PsiForeachStatement ||
        statement instanceof PsiDeclarationStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiSwitchLabelStatement) {
      return false;
    } else if (statement instanceof PsiThrowStatement) {
      return true;
    } else if (statement instanceof PsiForStatement) {
      return forStatementThrowsException((PsiForStatement) statement);
    } else if (statement instanceof PsiWhileStatement) {
      return whileStatementThrowsException((PsiWhileStatement) statement);
    } else if (statement instanceof PsiDoWhileStatement) {
      return doWhileThrowsException((PsiDoWhileStatement) statement);
    } else if (statement instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement) statement;
      PsiCodeBlock body = synchronizedStatement.getBody();
      return blockThrowsException(body);
    } else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement = (PsiBlockStatement) statement;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return blockThrowsException(codeBlock);
    } else if (statement instanceof PsiLabeledStatement) {
      PsiLabeledStatement labeledStatement = (PsiLabeledStatement) statement;
      PsiStatement statementLabeled = labeledStatement.getStatement();
      return statementThrowsException(statementLabeled);
    } else if (statement instanceof PsiIfStatement) {
      return ifStatementThrowsException((PsiIfStatement) statement);
    } else if (statement instanceof PsiTryStatement) {
      return tryStatementThrowsException((PsiTryStatement) statement);
    } else if (statement instanceof PsiSwitchStatement) {
      return false;
    } else {
      // unknown statement type
      return false;
    }
  }

  public static boolean blockThrowsException(@Nullable PsiCodeBlock block) {
    if (block == null) {
      return false;
    }
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementThrowsException(statement)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementThrowsException(PsiTryStatement tryStatement) {
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    if (catchBlocks.length == 0) {
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (blockThrowsException(tryBlock)) {
        return true;
      }
    }
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return blockThrowsException(finallyBlock);
  }

  private static boolean ifStatementThrowsException(PsiIfStatement ifStatement) {
    return statementThrowsException(ifStatement.getThenBranch()) && statementThrowsException(ifStatement.getElseBranch());
  }

  private static boolean doWhileThrowsException(PsiDoWhileStatement doWhileStatement) {
    return statementThrowsException(doWhileStatement.getBody());
  }

  private static boolean whileStatementThrowsException(PsiWhileStatement whileStatement) {
    PsiExpression condition = whileStatement.getCondition();
    if (BoolUtils.isTrue(condition)) {
      PsiStatement body = whileStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forStatementThrowsException(PsiForStatement forStatement) {
    PsiStatement initialization = forStatement.getInitialization();
    if (statementThrowsException(initialization)) {
      return true;
    }
    PsiExpression test = forStatement.getCondition();
    if (BoolUtils.isTrue(test)) {
      PsiStatement body = forStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
      PsiStatement update = forStatement.getUpdate();
      if (statementThrowsException(update)) {
        return true;
      }
    }
    return false;
  }

  private static class ExceptionsThrownVisitor extends JavaRecursiveElementVisitor {

    private final Set<PsiClassType> m_exceptionsThrown = new HashSet(4);

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiMethod method = expression.resolveMethod();
      collectExceptionsThrown(method, m_exceptionsThrown);
    }

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      PsiMethod method = expression.resolveMethod();
      collectExceptionsThrown(method, m_exceptionsThrown);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      PsiType type = exception.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      m_exceptionsThrown.add((PsiClassType) type);
    }

    @Override
    public void visitTryStatement(@Nonnull PsiTryStatement statement) {
      Set<PsiType> exceptionsHandled = getExceptionTypesHandled(statement);
      PsiResourceList resourceList = statement.getResourceList();
      if (resourceList != null) {
        List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
        for (PsiResourceVariable resourceVariable : resourceVariables) {
          Set<PsiClassType> resourceExceptions = calculateExceptionsThrown(resourceVariable);
          collectExceptionsThrown(PsiUtil.getResourceCloserMethod(resourceVariable), resourceExceptions);
          for (PsiClassType resourceException : resourceExceptions) {
            if (!isExceptionHandled(exceptionsHandled, resourceException)) {
              m_exceptionsThrown.add(resourceException);
            }
          }
        }
      }
      PsiCodeBlock tryBlock = statement.getTryBlock();
      if (tryBlock != null) {
        Set<PsiClassType> tryExceptions = calculateExceptionsThrown(tryBlock);
        for (PsiClassType tryException : tryExceptions) {
          if (!isExceptionHandled(exceptionsHandled, tryException)) {
            m_exceptionsThrown.add(tryException);
          }
        }
      }
      PsiCodeBlock finallyBlock = statement.getFinallyBlock();
      if (finallyBlock != null) {
        Set<PsiClassType> finallyExceptions = calculateExceptionsThrown(finallyBlock);
        m_exceptionsThrown.addAll(finallyExceptions);
      }

      PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
      for (PsiCodeBlock catchBlock : catchBlocks) {
        Set<PsiClassType> catchExceptions = calculateExceptionsThrown(catchBlock);
        m_exceptionsThrown.addAll(catchExceptions);
      }
    }

    private static void collectExceptionsThrown(@Nullable PsiMethod method, @Nonnull Set<PsiClassType> out) {
      if (method == null) {
        return;
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
      PsiJavaCodeReferenceElement[] referenceElements = method.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        PsiClass exceptionClass = (PsiClass) referenceElement.resolve();
        if (exceptionClass != null) {
          out.add(factory.createType(exceptionClass));
        }
      }
    }

    private static boolean isExceptionHandled(Iterable<PsiType> exceptionsHandled, PsiType thrownType) {
      for (PsiType exceptionHandled : exceptionsHandled) {
        if (exceptionHandled.isAssignableFrom(thrownType)) {
          return true;
        }
      }
      return false;
    }

    private static Set<PsiType> getExceptionTypesHandled(@Nonnull PsiTryStatement statement) {
      Set<PsiType> out = new HashSet<PsiType>(5);
      PsiParameter[] parameters = statement.getCatchBlockParameters();
      for (PsiParameter parameter : parameters) {
        PsiType type = parameter.getType();
        out.add(type);
      }
      return out;
    }

    @Nonnull
    public Set<PsiClassType> getExceptionsThrown() {
      return m_exceptionsThrown;
    }
  }
}