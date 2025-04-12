/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.psi.impl.search;

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.application.util.function.Processor;
import consulo.find.FindUsagesOptions;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Author: msk
 */
public class ThrowSearchUtil {

    private static final Logger LOG = Logger.getInstance(ThrowSearchUtil.class);

    private ThrowSearchUtil() {
    }

    public static class Root {
        final PsiElement myElement;
        final PsiType myType;
        final boolean isExact;

        public Root(final PsiElement root, final PsiType type, final boolean exact) {
            myElement = root;
            myType = type;
            isExact = exact;
        }

        public String toString() {
            return PsiFormatUtil.formatType(myType, PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
        }
    }

    public static Key<Root> THROW_SEARCH_ROOT_KEY = Key.create("ThrowSearchUtil.root");

    /**
     * @param aCatch
     * @param processor
     * @param root
     * @return true, if we should continue processing
     */
    private static boolean processExn(@Nonnull PsiParameter aCatch, @Nonnull Processor<UsageInfo> processor, @Nonnull Root root) {
        final PsiType type = aCatch.getType();
        if (type.isAssignableFrom(root.myType)) {
            processor.process(new UsageInfo(aCatch));
            return false;
        }
        if (!root.isExact && root.myType.isAssignableFrom(type)) {
            processor.process(new UsageInfo(aCatch));
            return true;
        }
        return true;
    }

    private static boolean scanCatches(
        @Nonnull PsiElement elem,
        @Nonnull Processor<UsageInfo> processor,
        @Nonnull Root root,
        @Nonnull FindUsagesOptions options,
        @Nonnull Set<PsiMethod> processed
    ) {
        while (elem != null) {
            final PsiElement parent = elem.getParent();
            if (elem instanceof PsiMethod) {
                final PsiMethod deepestSuperMethod = ((PsiMethod)elem).findDeepestSuperMethod();
                final PsiMethod method = deepestSuperMethod != null ? deepestSuperMethod : (PsiMethod)elem;
                if (!processed.contains(method)) {
                    processed.add(method);
                    final PsiReference[] refs =
                        MethodReferencesSearch.search(method, options.searchScope, true).toArray(PsiReference.EMPTY_ARRAY);
                    for (int i = 0; i != refs.length; ++i) {
                        if (!scanCatches(refs[i].getElement(), processor, root, options, processed)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            if (elem instanceof PsiTryStatement) {
                final PsiTryStatement aTry = (PsiTryStatement)elem;
                final PsiParameter[] catches = aTry.getCatchBlockParameters();
                for (int i = 0; i != catches.length; ++i) {
                    if (!processExn(catches[i], processor, root)) {
                        return false;
                    }
                }
            }
            else if (parent instanceof PsiTryStatement) {
                final PsiTryStatement tryStmt = (PsiTryStatement)parent;
                if (elem != tryStmt.getTryBlock()) {
                    elem = parent.getParent();
                    continue;
                }
            }
            elem = parent;
        }
        return true;
    }

    public static boolean addThrowUsages(@Nonnull Processor<UsageInfo> processor, @Nonnull Root root, @Nonnull FindUsagesOptions options) {
        Set<PsiMethod> processed = new HashSet<PsiMethod>();
        return scanCatches(root.myElement, processor, root, options, processed);
    }

    /**
     * @param exn
     * @return is type of exn exactly known
     */

    private static boolean isExactExnType(final PsiExpression exn) {
        return exn instanceof PsiNewExpression;
    }

    @Nullable
    public static Root[] getSearchRoots(final PsiElement element) {
        if (element instanceof PsiThrowStatement) {
            final PsiThrowStatement aThrow = (PsiThrowStatement)element;
            final PsiExpression exn = aThrow.getException();
            return new Root[]{new Root(aThrow.getParent(), exn.getType(), isExactExnType(exn))};
        }
        if (element instanceof PsiKeyword) {
            final PsiKeyword kwd = (PsiKeyword)element;
            if (PsiKeyword.THROWS.equals(kwd.getText())) {
                final PsiElement parent = kwd.getParent();
                if (parent != null && parent.getParent() instanceof PsiMethod) {
                    final PsiMethod method = (PsiMethod)parent.getParent();
                    final PsiReferenceList throwsList = method.getThrowsList();
                    final PsiClassType[] exns = throwsList.getReferencedTypes();
                    final Root[] roots = new Root[exns.length];
                    for (int i = 0; i != roots.length; ++i) {
                        final PsiClassType exn = exns[i];
                        roots[i] = new Root(method, exn, false); // TODO: test for final
                    }
                    return roots;
                }
            }
        }
        return null;
    }

    public static boolean isSearchable(final PsiElement element) {
        return getSearchRoots(element) != null;
    }

    public static String getSearchableTypeName(final PsiElement e) {
        if (e instanceof PsiThrowStatement) {
            final PsiThrowStatement aThrow = (PsiThrowStatement)e;
            final PsiType type = aThrow.getException().getType();
            return PsiFormatUtil.formatType(type, PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
        }
        if (e instanceof PsiKeyword && PsiKeyword.THROWS.equals(e.getText())) {
            return e.getParent().getText();
        }
        LOG.error("invalid searchable element");
        return e.getText();
    }
}
