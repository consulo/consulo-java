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

/*
 * @author max
 */
package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiClass;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.project.Project;
import consulo.util.lang.function.Condition;

import java.util.function.Predicate;

public class AllClassesSearch extends ExtensibleQueryFactory<PsiClass, AllClassesSearch.SearchParameters> {
    public static final AllClassesSearch INSTANCE = new AllClassesSearch();

    public static class SearchParameters {
        private final SearchScope myScope;
        private final Project myProject;
        private final Predicate<String> myShortNameCondition;

        public SearchParameters(SearchScope scope, Project project) {
            this(scope, project, Condition.TRUE);
        }

        public SearchParameters(SearchScope scope, Project project, Predicate<String> shortNameCondition) {
            myScope = scope;
            myProject = project;
            myShortNameCondition = shortNameCondition;
        }

        public SearchScope getScope() {
            return myScope;
        }

        public Project getProject() {
            return myProject;
        }

        public boolean nameMatches(String name) {
            return myShortNameCondition.test(name);
        }
    }

    private AllClassesSearch() {
        super(AllClassesSearchExecutor.class);
    }

    public static Query<PsiClass> search(SearchScope scope, Project project) {
        return INSTANCE.createQuery(new SearchParameters(scope, project));
    }

    public static Query<PsiClass> search(SearchScope scope, Project project, Predicate<String> shortNameCondition) {
        return INSTANCE.createQuery(new SearchParameters(scope, project, shortNameCondition));
    }
}