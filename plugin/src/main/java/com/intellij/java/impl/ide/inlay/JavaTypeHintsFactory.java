// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.inlay;

import com.intellij.java.language.psi.*;
import consulo.language.editor.inlay.DeclarativePresentationTreeBuilder;
import consulo.language.editor.inlay.InlayActionData;
import consulo.language.editor.inlay.InlayActionPayload;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class JavaTypeHintsFactory {
    private static final int START_FOLDING_FROM_LEVEL = 2;
    private static final String CAPTURE_OF = "capture of ";
    private static final String UNNAMED_MARK = "<unnamed>";
    private static final String ANONYMOUS_MARK = "anonymous";

    public static void typeHint(PsiType type, DeclarativePresentationTreeBuilder treeBuilder) {
        typeHint(treeBuilder, START_FOLDING_FROM_LEVEL, type);
    }

    private static void typeHint(DeclarativePresentationTreeBuilder builder, int level, PsiType type) {
        if (type instanceof PsiArrayType) {
            typeHint(builder, level + 1, ((PsiArrayType) type).getComponentType());
            builder.text("[]");
        }
        else if (type instanceof PsiClassType) {
            classTypeHint(builder, level, (PsiClassType) type);
        }
        else if (type instanceof PsiCapturedWildcardType) {
            builder.text(CAPTURE_OF);
            typeHint(builder, level, ((PsiCapturedWildcardType) type).getWildcard());
        }
        else if (type instanceof PsiWildcardType) {
            wildcardHint(builder, level, (PsiWildcardType) type);
        }
        else if (type instanceof PsiDisjunctionType) {
            join(builder,
                ((PsiDisjunctionType) type).getDisjunctions(),
                t -> typeHint(builder, level, t),
                () -> builder.text(" | "));
        }
        else if (type instanceof PsiIntersectionType) {
            join(builder,
                Arrays.asList(((PsiIntersectionType) type).getConjuncts()),
                t -> typeHint(builder, level, t),
                () -> builder.text(" & "));
        }
        else {
            builder.text(type.getPresentableText());
        }
    }

    private static void wildcardHint(DeclarativePresentationTreeBuilder builder, int level, PsiWildcardType type) {
        if (type.isExtends()) {
            builder.text("extends ");
            typeHint(builder, level, type.getExtendsBound());
        }
        else if (type.isSuper()) {
            builder.text("super ");
            typeHint(builder, level, type.getSuperBound());
        }
        else {
            builder.text("?");
        }
    }

    private static void classTypeHint(DeclarativePresentationTreeBuilder builder, int level, PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        String className = classType.getClassName() != null ? classType.getClassName() : ANONYMOUS_MARK;
        builder.text(className,
            aClass != null && aClass.getQualifiedName() != null
                ? new InlayActionData(new InlayActionPayload.StringInlayActionPayload(aClass.getQualifiedName()),
                JavaFqnDeclarativeInlayActionHandler.HANDLER_NAME)
                : null);
        if (classType.getParameterCount() == 0) {
            return;
        }
        builder.collapsibleList(
            (b) -> {
                b.toggleButton((next) -> builder.text("<"));
                join(builder,
                    Arrays.asList(classType.getParameters()),
                    t -> typeHint(builder, level + 1, t),
                    () -> builder.text(", "));
                b.toggleButton((next) -> builder.text(">"));
            },
            (b) -> b.toggleButton((next) -> builder.text("<...>"))
        );
    }

    private static <T> void join(DeclarativePresentationTreeBuilder builder,
                                 List<T> elements,
                                 Consumer<T> op,
                                 Runnable separator) {
        boolean first = true;
        for (T element : elements) {
            if (!first) {
                separator.run();
            }
            else {
                first = false;
            }
            op.accept(element);
        }
    }
}
