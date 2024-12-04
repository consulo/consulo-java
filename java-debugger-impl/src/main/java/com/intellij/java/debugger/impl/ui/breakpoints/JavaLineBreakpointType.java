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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.HelpID;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaBreakpointProperties;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaLineBreakpointProperties;
import com.intellij.java.debugger.impl.engine.PositionManagerImpl;
import com.intellij.java.language.psi.PsiLambdaExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointGroupingRule;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

/**
 * Base class for java line-connected exceptions (line, method, field)
 *
 * @author egor
 */
@ExtensionImpl
public class JavaLineBreakpointType extends JavaLineBreakpointTypeBase<JavaLineBreakpointProperties> implements JavaBreakpointType<JavaLineBreakpointProperties> {
    @Nonnull
    public static JavaLineBreakpointType getInstance() {
        return EXTENSION_POINT_NAME.findExtension(JavaLineBreakpointType.class);
    }

    public JavaLineBreakpointType() {
        super("java-line", DebuggerBundle.message("line.breakpoints.tab.title"));
    }

    protected JavaLineBreakpointType(@NonNls @Nonnull String id, @Nls @Nonnull String title) {
        super(id, title);
    }

    //@Override
    protected String getHelpID() {
        return HelpID.LINE_BREAKPOINTS;
    }

    //@Override
    public String getDisplayName() {
        return DebuggerBundle.message("line.breakpoints.tab.title");
    }

    @Override
    public List<XBreakpointGroupingRule<XLineBreakpoint<JavaLineBreakpointProperties>, ?>> getGroupingRules() {
        return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
    }

    @Nullable
    @Override
    public JavaLineBreakpointProperties createProperties() {
        return new JavaLineBreakpointProperties();
    }

    @Nonnull
    @Override
    public JavaLineBreakpointProperties createBreakpointProperties(@Nonnull VirtualFile file, int line) {
        return new JavaLineBreakpointProperties();
    }

    @Nonnull
    @Override
    public Breakpoint<JavaLineBreakpointProperties> createJavaBreakpoint(Project project, XBreakpoint breakpoint) {
        return new LineBreakpoint<>(project, breakpoint);
    }

    @Nonnull
    @Override
    public List<JavaBreakpointVariant> computeVariants(@Nonnull Project project, @Nonnull XSourcePosition position) {
        SourcePosition pos = DebuggerUtilsEx.toSourcePosition(position, project);
        if (pos == null) {
            return Collections.emptyList();
        }

        List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(pos, true);
        if (lambdas.isEmpty()) {
            return Collections.emptyList();
        }

        PsiElement startMethod = DebuggerUtilsEx.getContainingMethod(pos);
        //noinspection SuspiciousMethodCalls
        if (lambdas.contains(startMethod) && lambdas.size() == 1) {
            return Collections.emptyList();
        }

        Document document = PsiDocumentManager.getInstance(project).getDocument(pos.getFile());
        if (document == null) {
            return Collections.emptyList();
        }

        List<JavaBreakpointVariant> res = new SmartList<>();

        if (!(startMethod instanceof PsiLambdaExpression)) {
            res.add(new LineJavaBreakpointVariant(position, startMethod, -1)); // base method
        }

        int ordinal = 0;
        for (PsiLambdaExpression lambda : lambdas)   //lambdas
        {
            PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambda, document, position.getLine());
            XSourcePosition elementPosition = XDebuggerUtil.getInstance().createPositionByElement(firstElem);
            if (elementPosition != null) {
                if (lambda == startMethod) {
                    res.add(0, new LineJavaBreakpointVariant(elementPosition, lambda, ordinal++));
                }
                else {
                    res.add(new LambdaJavaBreakpointVariant(elementPosition, lambda, ordinal++));
                }
            }
        }

        res.add(new JavaBreakpointVariant(position)); //all

        return res;
    }

    public boolean matchesPosition(@Nonnull LineBreakpoint<?> breakpoint, @Nonnull SourcePosition position) {
        JavaBreakpointProperties properties = breakpoint.getProperties();
        if (properties instanceof JavaLineBreakpointProperties) {
            if (!(breakpoint instanceof RunToCursorBreakpoint) && ((JavaLineBreakpointProperties) properties).getLambdaOrdinal() == null) {
                return true;
            }
            PsiElement containingMethod = getContainingMethod(breakpoint);
            if (containingMethod == null) {
                return false;
            }
            return DebuggerUtilsEx.inTheMethod(position, containingMethod);
        }
        return true;
    }

    @Nullable
    public PsiElement getContainingMethod(@Nonnull LineBreakpoint<?> breakpoint) {
        SourcePosition position = breakpoint.getSourcePosition();
        if (position == null) {
            return null;
        }

        JavaBreakpointProperties properties = breakpoint.getProperties();
        if (properties instanceof JavaLineBreakpointProperties && !(breakpoint instanceof RunToCursorBreakpoint)) {
            Integer ordinal = ((JavaLineBreakpointProperties) properties).getLambdaOrdinal();
            if (ordinal > -1) {
                List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(position, true);
                if (ordinal < lambdas.size()) {
                    return lambdas.get(ordinal);
                }
            }
        }
        return DebuggerUtilsEx.getContainingMethod(position);
    }

    public class JavaBreakpointVariant extends XLineBreakpointAllVariant {
        public JavaBreakpointVariant(@Nonnull XSourcePosition position) {
            super(position);
        }
    }

    public class ExactJavaBreakpointVariant extends JavaBreakpointVariant {
        private final PsiElement myElement;
        private final Integer myLambdaOrdinal;

        public ExactJavaBreakpointVariant(@Nonnull XSourcePosition position, @Nullable PsiElement element, Integer lambdaOrdinal) {
            super(position);
            myElement = element;
            myLambdaOrdinal = lambdaOrdinal;
        }

        @RequiredReadAction
        @Override
        public Image getIcon() {
            return myElement != null ? IconDescriptorUpdaters.getIcon(myElement, 0) : ExecutionDebugIconGroup.breakpointBreakpoint();
        }

        @RequiredReadAction
        @Override
        public String getText() {
            return myElement != null ? StringUtil.shortenTextWithEllipsis(myElement.getText(), 100, 0) : "Line";
        }

        @RequiredReadAction
        @Override
        public TextRange getHighlightRange() {
            return myElement != null ? myElement.getTextRange() : null;
        }

        @Nonnull
        @Override
        public JavaLineBreakpointProperties createProperties() {
            JavaLineBreakpointProperties properties = super.createProperties();
            assert properties != null;
            properties.setLambdaOrdinal(myLambdaOrdinal);
            return properties;
        }
    }

    public class LineJavaBreakpointVariant extends ExactJavaBreakpointVariant {
        public LineJavaBreakpointVariant(@Nonnull XSourcePosition position, @Nullable PsiElement element, Integer lambdaOrdinal) {
            super(position, element, lambdaOrdinal);
        }

        @Override
        public String getText() {
            return "Line";
        }

        @Override
        public Image getIcon() {
            return ExecutionDebugIconGroup.breakpointBreakpoint();
        }
    }

    public class LambdaJavaBreakpointVariant extends ExactJavaBreakpointVariant {
        public LambdaJavaBreakpointVariant(@Nonnull XSourcePosition position, @Nonnull PsiElement element, Integer lambdaOrdinal) {
            super(position, element, lambdaOrdinal);
        }

        @Override
        public Image getIcon() {
            return ExecutionDebugIconGroup.breakpointBreakpointlambda();
        }
    }

    @Nullable
    @Override
    public TextRange getHighlightRange(XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
        Integer ordinal = getLambdaOrdinal(breakpoint);
        if (ordinal != null) {
            Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
            if (javaBreakpoint instanceof LineBreakpoint) {
                PsiElement method = getContainingMethod((LineBreakpoint) javaBreakpoint);
                if (method != null) {
                    return DebuggerUtilsEx.intersectWithLine(method.getTextRange(), method.getContainingFile(), breakpoint.getLine());
                }
            }
        }
        return null;
    }

    @Override
    public XSourcePosition getSourcePosition(@Nonnull XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
        Integer ordinal = getLambdaOrdinal(breakpoint);
        if (ordinal != null && ordinal > -1) {
            SourcePosition linePosition = createLineSourcePosition((XLineBreakpoint) breakpoint);
            if (linePosition != null) {
                return DebuggerUtilsEx.toXSourcePosition(new PositionManagerImpl.JavaSourcePosition(linePosition, ordinal));
            }
        }
        return null;
    }

    @Nullable
    private static Integer getLambdaOrdinal(XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
        JavaLineBreakpointProperties properties = breakpoint.getProperties();
        return properties != null ? properties.getLambdaOrdinal() : null;
    }

    @Nullable
    private static SourcePosition createLineSourcePosition(XLineBreakpoint breakpoint) {
        VirtualFile file = breakpoint.getFile();
        if (file != null) {
            PsiFile psiFile = PsiManager.getInstance(breakpoint.getProject()).findFile(file);
            if (psiFile != null) {
                return SourcePosition.createFromLine(psiFile, breakpoint.getLine());
            }
        }
        return null;
    }

    @Override
    public boolean canBeHitInOtherPlaces() {
        return true; // line breakpoints could be hit in other versions of the same classes
    }
}
