package com.intellij.java.language.psi.search;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.IdFilter;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.function.Predicate;

/**
 * @author VISTALL
 * @since 2022-12-09
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface PsiShortNameProvider {
    /**
     * Returns the list of files with the specified name.
     *
     * @param name the name of the files to find.
     * @return the list of files in the project which have the specified name.
     */
    @Nonnull
    default PsiFile[] getFilesByName(@Nonnull String name) {
        return PsiFile.EMPTY_ARRAY;
    }

    /**
     * Returns the list of names of all files in the project.
     *
     * @return the list of all file names in the project.
     */
    @Nonnull
    default String[] getAllFileNames() {
        return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    /**
     * Returns the list of all classes with the specified name in the specified scope.
     *
     * @param name  the non-qualified name of the classes to find.
     * @param scope the scope in which classes are searched.
     * @return the list of found classes.
     */
    @Nonnull
    public abstract PsiClass[] getClassesByName(@Nonnull String name, @Nonnull GlobalSearchScope scope);

    /**
     * Returns the list of names of all classes in the project and
     * (optionally) libraries.
     *
     * @return the list of all class names.
     */
    @Nonnull
    public abstract String[] getAllClassNames();

    default boolean processAllClassNames(Predicate<String> processor) {
        return ContainerUtil.process(getAllClassNames(), processor);
    }

    default boolean processAllClassNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return ContainerUtil.process(getAllClassNames(), processor);
    }

    /**
     * Adds the names of all classes in the project and (optionally) libraries
     * to the specified set.
     *
     * @param dest the set to add the names to.
     */
    public abstract void getAllClassNames(@Nonnull HashSet<String> dest);

    /**
     * Returns the list of all methods with the specified name in the specified scope.
     *
     * @param name  the name of the methods to find.
     * @param scope the scope in which methods are searched.
     * @return the list of found methods.
     */
    @Nonnull
    public abstract PsiMethod[] getMethodsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope);

    @Nonnull
    public abstract PsiMethod[] getMethodsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount);

    @Nonnull
    public abstract PsiField[] getFieldsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount);

    public abstract boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiMethod> processor
    );

    public abstract boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiMethod> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    );

    default boolean processAllMethodNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return ContainerUtil.process(getAllFieldNames(), processor);
    }

    default boolean processAllFieldNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return ContainerUtil.process(getAllFieldNames(), processor);
    }

    /**
     * Returns the list of names of all methods in the project and
     * (optionally) libraries.
     *
     * @return the list of all method names.
     */
    @Nonnull
    public abstract String[] getAllMethodNames();

    /**
     * Adds the names of all methods in the project and (optionally) libraries
     * to the specified set.
     *
     * @param set the set to add the names to.
     */
    public abstract void getAllMethodNames(@Nonnull HashSet<String> set);

    /**
     * Returns the list of all fields with the specified name in the specified scope.
     *
     * @param name  the name of the fields to find.
     * @param scope the scope in which fields are searched.
     * @return the list of found fields.
     */
    @Nonnull
    public abstract PsiField[] getFieldsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope);

    /**
     * Returns the list of names of all fields in the project and
     * (optionally) libraries.
     *
     * @return the list of all field names.
     */
    @Nonnull
    public abstract String[] getAllFieldNames();

    /**
     * Adds the names of all methods in the project and (optionally) libraries
     * to the specified set.
     *
     * @param set the set to add the names to.
     */
    public abstract void getAllFieldNames(@Nonnull HashSet<String> set);

    public abstract boolean processFieldsWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiField> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    );

    public abstract boolean processClassesWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiClass> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    );
}
