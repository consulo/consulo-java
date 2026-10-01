/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package consulo.java.diagram.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.component.util.ModificationTracker;
import consulo.diagram.DiagramColorManager;
import consulo.diagram.DiagramDataModel;
import consulo.diagram.DiagramEdgeCreationPolicy;
import consulo.diagram.DiagramProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class JavaDiagramProvider extends DiagramProvider<PsiElement> {
    public static final String ID = "JAVA";

    private final JavaDiagramVisibilityManager myVisibilityManager = new JavaDiagramVisibilityManager();
    private final JavaDiagramCategoryManager myCategoryManager = new JavaDiagramCategoryManager();
    private final JavaDiagramElementManager myElementManager = new JavaDiagramElementManager();
    private final JavaDiagramVfsResolver myVfsResolver = new JavaDiagramVfsResolver();
    private final JavaDiagramColorManager myColorManager = new JavaDiagramColorManager();
    private final JavaDiagramRelationshipManager myRelationshipManager = new JavaDiagramRelationshipManager();
    private final JavaDiagramExtras myExtras = new JavaDiagramExtras();
    private final JavaDiagramEdgeCreationPolicy myEdgeCreationPolicy = new JavaDiagramEdgeCreationPolicy();

    public JavaDiagramProvider() {
        myElementManager.setDiagramProvider(this);
    }

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public JavaDiagramVisibilityManager getVisibilityManager() {
        return myVisibilityManager;
    }

    @Override
    public JavaDiagramCategoryManager getNodeContentManager() {
        return myCategoryManager;
    }

    @Override
    public JavaDiagramElementManager getElementManager() {
        return myElementManager;
    }

    @Override
    public JavaDiagramVfsResolver getVfsResolver() {
        return myVfsResolver;
    }

    @Override
    public JavaDiagramRelationshipManager getRelationshipManager() {
        return myRelationshipManager;
    }

    @Override
    public DiagramDataModel<PsiElement> createDataModel(Project project, @Nullable PsiElement element, @Nullable VirtualFile file) {
        return new JavaDiagramDataModel(project, this, element, file);
    }

    @Override
    public ModificationTracker getModificationTracker(Project project) {
        return PsiManager.getInstance(project).getModificationTracker();
    }

    @Override
    public DiagramColorManager getColorManager() {
        return myColorManager;
    }

    @Override
    public JavaDiagramExtras getExtras() {
        return myExtras;
    }

    @Override
    public DiagramEdgeCreationPolicy<PsiElement> getEdgeCreationPolicy() {
        return myEdgeCreationPolicy;
    }
}
