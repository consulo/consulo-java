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
import consulo.annotation.access.RequiredReadAction;
import consulo.find.FindUsagesOptions;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Author: msk
 */
public class ThrowSearchUtil {

    private static final Logger LOG = Logger.getInstance(ThrowSearchUtil.class);

    private ThrowSearchUtil() {
    }

    public static class Root {
        PsiElement myElement;
        PsiType myType;
        boolean isExact;

        public Root(PsiElement root, PsiType type, boolean exact) {
            myElement = root;
            myType = type;
            isExact = exact;
        }

        @Override
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
    private static boolean processExn(@Nonnull PsiParameter aCatch, @Nonnull Predicate<UsageInfo> processor, @Nonnull Root root) {
        PsiType type = aCatch.getType();
        if (type.isAssignableFrom(root.myType)) {
            processor.test(new UsageInfo(aCatch));
            return false;
        }
        if (!root.isExact && root.myType.isAssignableFrom(type)) {
            processor.test(new UsageInfo(aCatch));
            return true;
        }
        return true;
    }

    @RequiredReadAction
    private static boolean scanCatches(
        @Nonnull PsiElement elem,
        @Nonnull Predicate<UsageInfo> processor,
        @Nonnull Root root,
        @Nonnull FindUsagesOptions options,
        @Nonnull Set<PsiMethod> processed
    ) {
        while (elem != null) {
            PsiElement parent = elem.getParent();
            if (elem instanceof PsiMethod m) {
                PsiMethod method = ObjectUtil.chooseNotNull(m.findDeepestSuperMethod(), m);
                if (!processed.contains(method)) {
                    processed.add(method);
                    PsiReference[] refs =
                        MethodReferencesSearch.search(method, options.searchScope, true).toArray(PsiReference.EMPTY_ARRAY);
                    for (int i = 0; i != refs.length; ++i) {
                        if (!scanCatches(refs[i].getElement(), processor, root, options, processed)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            if (elem instanceof PsiTryStatement tryStmt) {
                PsiParameter[] catches = tryStmt.getCatchBlockParameters();
                for (int i = 0; i != catches.length; ++i) {
                    if (!processExn(catches[i], processor, root)) {
                        return false;
                    }
                }
            }
            else if (parent instanceof PsiTryStatement tryStmt) {
                if (elem != tryStmt.getTryBlock()) {
                    elem = parent.getParent();
                    continue;
                }
            }
            elem = parent;
        }
        return true;
    }

    @RequiredReadAction
    public static boolean addThrowUsages(@Nonnull Predicate<UsageInfo> processor, @Nonnull Root root, @Nonnull FindUsagesOptions options) {
        Set<PsiMethod> processed = new HashSet<>();
        return scanCatches(root.myElement, processor, root, options, processed);
    }

    /**
     * @param exn
     * @return is type of exn exactly known
     */

    private static boolean isExactExnType(PsiExpression exn) {
        return exn instanceof PsiNewExpression;
    }

    @Nullable
    public static Root[] getSearchRoots(PsiElement element) {
        if (element instanceof PsiThrowStatement aThrow) {
            PsiExpression exn = aThrow.getException();
            return new Root[]{new Root(aThrow.getParent(), exn.getType(), isExactExnType(exn))};
        }
        if (element instanceof PsiKeyword kwd && PsiKeyword.THROWS.equals(kwd.getText())) {
            PsiElement parent = kwd.getParent();
            if (parent != null && parent.getParent() instanceof PsiMethod method) {
                PsiReferenceList throwsList = method.getThrowsList();
                PsiClassType[] exns = throwsList.getReferencedTypes();
                Root[] roots = new Root[exns.length];
                for (int i = 0; i != roots.length; ++i) {
                    PsiClassType exn = exns[i];
                    roots[i] = new Root(method, exn, false); // TODO: test for final
                }
                return roots;
            }
        }
        return null;
    }

    public static boolean isSearchable(PsiElement element) {
        return getSearchRoots(element) != null;
    }

    @RequiredReadAction
    public static String getSearchableTypeName(PsiElement e) {
        if (e instanceof PsiThrowStatement aThrow) {
            PsiType type = aThrow.getException().getType();
            return PsiFormatUtil.formatType(type, PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
        }
        if (e instanceof PsiKeyword && PsiKeyword.THROWS.equals(e.getText())) {
            return e.getParent().getText();
        }
        LOG.error("invalid searchable element");
        return e.getText();
    }
}
