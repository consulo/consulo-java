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
package com.intellij.java.impl.psi.util.proximity;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.proximity.ProximityLocation;
import consulo.language.util.proximity.ProximityWeigher;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;

import static consulo.java.language.module.util.JavaClassNames.*;

/**
 * @author peter
 */
@ExtensionImpl(id = "knownElement", order = "after sameModule, before sdkOrLibrary")
public class KnownElementWeigher extends ProximityWeigher {
    private static final Set<String> POPULAR_JDK_CLASSES = Set.of(JAVA_LANG_STRING, JAVA_LANG_CLASS, System.class.getName(), JAVA_LANG_RUNNABLE, JAVA_LANG_EXCEPTION,
        JAVA_LANG_THROWABLE, JAVA_LANG_RUNTIME_EXCEPTION, JAVA_UTIL_ARRAY_LIST, JAVA_UTIL_HASH_MAP, JAVA_UTIL_HASH_SET);

    @Override
    public Comparable weigh(@Nonnull final PsiElement element, @Nonnull final ProximityLocation location) {
        Project project = location.getProject();
        if (project == null) {
            return 0;
        }

        Comparable tests = getTestFrameworkWeight(element, location, project);
        if (tests != null) {
            return tests;
        }

        if (!isSdkElement(element, project)) {
            return 0;
        }

        if (element instanceof PsiClass) {
            return getJdkClassProximity((PsiClass) element);
        }
        if (element instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod) element;
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String methodName = method.getName();
                if ("finalize".equals(methodName)
                    || "registerNatives".equals(methodName)
                    || methodName.startsWith("wait")
                    || methodName.startsWith("notify")) {
                    if (JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
                        return -1;
                    }
                }
                if (isGetClass(method)) {
                    return -1;
                }
                if ("subSequence".equals(methodName)) {
                    if (JAVA_LANG_STRING.equals(containingClass.getQualifiedName())) {
                        return -1;
                    }
                }
                if (JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
                    return 0;
                }
                return getJdkClassProximity(method.getContainingClass());
            }
        }
        if (element instanceof PsiField) {
            return getJdkClassProximity(((PsiField) element).getContainingClass());
        }
        return 0;
    }

    public static boolean isSdkElement(PsiElement element, @Nonnull final Project project) {
        final VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
            List<OrderEntry> orderEntries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
            if (!orderEntries.isEmpty() && orderEntries.get(0) instanceof ModuleExtensionWithSdkOrderEntry) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Integer getTestFrameworkWeight(@Nonnull PsiElement element, @Nonnull ProximityLocation location, @Nonnull Project project) {
        if (element instanceof PsiClass) {
            final String qualifiedName = ((PsiClass) element).getQualifiedName();
            if (qualifiedName != null) {
                if (qualifiedName.startsWith("org.testng.internal")) {
                    return -1;
                }
                VirtualFile locationFile = PsiUtilCore.getVirtualFile(location.getPosition());
                if (locationFile != null && ProjectFileIndex.getInstance(project).isInTestSourceContent(locationFile) && (qualifiedName.contains("junit") || qualifiedName.contains("test"))) {
                    return 1;
                }
            }
        }
        return null;
    }

    public static boolean isGetClass(PsiMethod method) {
        return "getClass".equals(method.getName()) && method.getParameterList().getParametersCount() <= 0;
    }

    private static Comparable getJdkClassProximity(@Nullable PsiClass element) {
        if (element == null || element.getContainingClass() != null) {
            return 0;
        }

        final String qname = element.getQualifiedName();
        if (qname != null) {
            String pkg = StringUtil.getPackageName(qname);
            if (qname.equals(JAVA_LANG_OBJECT)) {
                return 5;
            }
            if (POPULAR_JDK_CLASSES.contains(qname)) {
                return 8;
            }
            if (pkg.equals("java.lang")) {
                return 6;
            }
            if (pkg.equals("java.util")) {
                return 7;
            }

            if (qname.startsWith("java.lang")) {
                return 5;
            }
            if (qname.startsWith("java.util")) {
                return 4;
            }

            if (pkg.equals("javax.swing")) {
                return 3;
            }
            if (qname.startsWith("java.")) {
                return 2;
            }
            if (qname.startsWith("javax.")) {
                return 1;
            }
            if (qname.startsWith("com.")) {
                return -1;
            }
            if (qname.startsWith("net.")) {
                return -1;
            }
        }
        return 0;
    }

}