/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.impl.codeInsight.editorActions.wordSelection.DocTagSelectioner;
import com.intellij.java.impl.codeInsight.lookup.LookupItemUtil;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.javadoc.JavaDocUtil;
import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.*;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.filter.TrueFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Predicates;
import jakarta.annotation.Nonnull;

import java.util.*;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.string;

/**
 * @author ik
 * @since 2003-03-05
 */
@ExtensionImpl(id = "javadoc", order = "last, before javaLegacy")
public class JavaDocCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(JavaDocCompletionContributor.class);
    private static final String VALUE_TAG = "value";
    private static final String LINK_TAG = "link";

    private static final InsertHandler<LookupElement> PARAM_DESCRIPTION_INSERT_HANDLER = (context, item) -> {
        if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) {
            return;
        }

        context.commitDocument();
        PsiDocTag docTag = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiDocTag.class, false);
        if (docTag != null) {
            Document document = context.getDocument();
            int tagEnd = DocTagSelectioner.getDocTagRange(docTag, document.getCharsSequence(), 0).getEndOffset();
            int tail = context.getTailOffset();
            if (tail < tagEnd) {
                document.deleteString(tail, tagEnd);
            }
        }
    };

    public JavaDocCompletionContributor() {
        extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_TAG_NAME), new TagChooser());

        extend(
            CompletionType.BASIC,
            PsiJavaPatterns.psiElement().inside(PsiDocComment.class),
            (parameters, context, result) -> {
                PsiElement position = parameters.getPosition();
                boolean isArg = PsiJavaPatterns.psiElement().afterLeaf("(").accepts(position);
                PsiDocTag tag = PsiTreeUtil.getParentOfType(position, PsiDocTag.class);
                boolean onlyConstants = !isArg && tag != null && tag.getName().equals(VALUE_TAG);

                PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
                if (ref instanceof PsiJavaReference javaRef) {
                    result.stopHere();

                    for (LookupElement item : completeJavadocReference(position, javaRef)) {
                        if (onlyConstants) {
                            Object o = item.getObject();
                            if (!(o instanceof PsiField field)) {
                                continue;
                            }
                            if (!(field.isStatic() && field.getInitializer() != null
                                && JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) != null)) {
                                continue;
                            }
                        }

                        if (isArg) {
                            item = AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item);
                        }
                        result.addElement(item);
                    }

                    JavaCompletionContributor.addAllClasses(parameters, result, new JavaCompletionSession(result));
                }

                if (tag != null && "author".equals(tag.getName())) {
                    result.addElement(LookupElementBuilder.create(Platform.current().user().name()));
                }
            }
        );

        extend(
            CompletionType.SMART,
            psiElement().inside(psiElement(PsiDocTag.class).withName(string().oneOf(PsiKeyword.THROWS, "exception"))),
            (parameters, context, result) -> {
                PsiElement element = parameters.getPosition();
                Set<PsiClass> throwsSet = new HashSet<>();
                PsiMethod method = PsiTreeUtil.getContextOfType(element, PsiMethod.class, true);
                if (method != null) {
                    for (PsiClassType ref : method.getThrowsList().getReferencedTypes()) {
                        PsiClass exception = ref.resolve();
                        if (exception != null && throwsSet.add(exception)) {
                            result.addElement(TailTypeDecorator.withTail(
                                new JavaPsiClassReferenceElement(exception),
                                TailType.HUMBLE_SPACE_BEFORE_WORD
                            ));
                        }
                    }
                }
            }
        );
    }

    @Nonnull
    @RequiredReadAction
    private List<LookupElement> completeJavadocReference(PsiElement position, PsiJavaReference ref) {
        JavaCompletionProcessor processor = new JavaCompletionProcessor(
            position,
            TrueFilter.INSTANCE,
            JavaCompletionProcessor.Options.CHECK_NOTHING,
            Predicates.alwaysTrue()
        );
        ref.processVariants(processor);
        return ContainerUtil.map(
            processor.getResults(),
            completionResult -> {
                LookupElement item = createReferenceLookupItem(completionResult.getElement());
                item.putUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
                return item;
            }
        );
    }

    private LookupElement createReferenceLookupItem(Object element) {
        if (element instanceof PsiMethod method) {
            return new JavaMethodCallElement(method) {
                @Override
                @RequiredUIAccess
                public void handleInsert(InsertionContext context) {
                    new MethodSignatureInsertHandler().handleInsert(context, this);
                }
            };
        }
        if (element instanceof PsiClass psiClass) {
            JavaPsiClassReferenceElement classElement = new JavaPsiClassReferenceElement(psiClass);
            classElement.setInsertHandler(JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER);
            return classElement;
        }

        return LookupItemUtil.objectToLookupItem(element);
    }

    @RequiredReadAction
    private static PsiParameter getDocTagParam(PsiElement tag) {
        if (tag instanceof PsiDocTag docTag
            && "param".equals(docTag.getName())
            && docTag.getValueElement() instanceof PsiDocParamRef docParamRef
            && docParamRef.getReference() instanceof PsiReference psiReference
            && psiReference.resolve() instanceof PsiParameter param) {
            return param;
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (PsiJavaPatterns.psiElement(JavaDocTokenType.DOC_COMMENT_DATA).accepts(position)) {
            PsiParameter param = getDocTagParam(position.getParent());
            if (param != null) {
                suggestSimilarParameterDescriptions(result, position, param);
            }

            suggestLinkWrappingVariants(
                parameters,
                result.withPrefixMatcher(CompletionUtilCore.findJavaIdentifierPrefix(parameters)),
                position
            );

            if (!result.getPrefixMatcher().getPrefix().isEmpty()) {
                for (String keyword : Set.of("null", "true", "false")) {
                    String tagText = "{@code " + keyword + "}";
                    result.addElement(
                        LookupElementBuilder.create(keyword)
                            .withPresentableText(tagText)
                            .withInsertHandler(
                                (context, item) -> context.getDocument()
                                    .replaceString(context.getStartOffset(), context.getTailOffset(), tagText)
                            )
                    );
                }
            }

            return;
        }

        super.fillCompletionVariants(parameters, result);
    }

    @RequiredReadAction
    private void suggestLinkWrappingVariants(
        @Nonnull CompletionParameters parameters,
        @Nonnull CompletionResultSet result,
        PsiElement position
    ) {
        PrefixMatcher matcher = result.getPrefixMatcher();
        int prefixStart = parameters.getOffset() - matcher.getPrefix().length() - position.getTextRange().getStartOffset();
        if (prefixStart > 0 && position.getText().charAt(prefixStart - 1) == '#') {
            String mockCommentPrefix = "/** {@link ";
            String mockText = mockCommentPrefix + position.getText().substring(prefixStart - 1) + "}*/";
            PsiDocComment mockComment = JavaPsiFacade.getElementFactory(position.getProject()).createDocCommentFromText(mockText, position);
            PsiJavaReference ref = (PsiJavaReference) mockComment.findReferenceAt(mockCommentPrefix.length() + 1);
            assert ref != null : mockText;
            for (LookupElement element : completeJavadocReference(ref.getElement(), ref)) {
                result.addElement(LookupElementDecorator.withInsertHandler(
                    element,
                    wrapIntoLinkTag((context, item) -> element.handleInsert(context))
                ));
            }
        }
        else {
            InsertHandler<JavaPsiClassReferenceElement> handler = wrapIntoLinkTag(JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER);
            AllClassesGetter.processJavaClasses(
                parameters,
                matcher,
                parameters.getInvocationCount() == 1,
                psiClass -> result.addElement(AllClassesGetter.createLookupItem(psiClass, handler))
            );
        }
    }

    @Nonnull
    private static <T extends LookupElement> InsertHandler<T> wrapIntoLinkTag(InsertHandler<T> delegate) {
        return (context, item) ->
        {
            Document document = context.getDocument();

            String link = "{@link ";
            int startOffset = context.getStartOffset();
            int sharpLength = document.getCharsSequence().charAt(startOffset - 1) == '#' ? 1 : 0;

            document.insertString(startOffset - sharpLength, link);
            document.insertString(context.getTailOffset(), "}");
            context.setTailOffset(context.getTailOffset() - 1);
            context.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, startOffset + link.length());

            context.commitDocument();
            delegate.handleInsert(context, item);
            if (item.getObject() instanceof PsiField) {
                context.getEditor().getCaretModel().moveToOffset(context.getTailOffset() + 1);
            }
        };
    }

    private static void suggestSimilarParameterDescriptions(CompletionResultSet result, PsiElement position, final PsiParameter param) {
        final Set<String> descriptions = new HashSet<>();
        position.getContainingFile().accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                PsiParameter param1 = getDocTagParam(element);
                if (param1 != null && param1 != param
                    && Objects.equals(param1.getName(), param.getName())
                    && Objects.equals(param1.getType(), param.getType())) {
                    String text = "";
                    PsiDocTag docTag = (PsiDocTag) element;
                    for (PsiElement psiElement : docTag.getDataElements()) {
                        if (psiElement != docTag.getValueElement()) {
                            text += psiElement.getText();
                        }
                    }
                    text = text.trim();
                    if (text.contains(" ")) {
                        descriptions.add(text);
                    }
                }

                super.visitElement(element);
            }
        });
        for (String description : descriptions) {
            result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(description)
                .withInsertHandler(PARAM_DESCRIPTION_INSERT_HANDLER), 1));
        }
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    private static class TagChooser implements CompletionProvider {
        @Override
        @RequiredReadAction
        public void addCompletions(
            @Nonnull CompletionParameters parameters,
            ProcessingContext context,
            @Nonnull CompletionResultSet result
        ) {
            List<String> ret = new ArrayList<>();
            PsiElement position = parameters.getPosition();
            PsiDocComment comment = PsiTreeUtil.getParentOfType(position, PsiDocComment.class);
            assert comment != null;
            PsiElement parent = comment.getContext();
            if (parent instanceof PsiJavaFile file && PsiJavaPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
                String packageName = file.getPackageName();
                parent = JavaPsiFacade.getInstance(position.getProject()).findPackage(packageName);
            }

            boolean isInline = position.getContext() instanceof PsiInlineDocTag;

            for (JavadocTagInfo info : JavadocManager.SERVICE.getInstance(position.getProject()).getTagInfos(parent)) {
                String tagName = info.getName();
                if (tagName.equals(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) {
                    continue;
                }
                if (isInline != info.isInline()) {
                    continue;
                }
                ret.add(tagName);
                addSpecialTags(ret, comment, tagName);
            }

            InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(position.getProject()).getInspectionProfile();
            JavaDocLocalInspection inspection =
                (JavaDocLocalInspection) inspectionProfile.getUnwrappedTool(JavaDocLocalInspection.SHORT_NAME, position);
            if (inspection != null) {
                StringTokenizer tokenizer = new StringTokenizer(inspection.myAdditionalJavadocTags, ", ");
                while (tokenizer.hasMoreTokens()) {
                    ret.add(tokenizer.nextToken());
                }
            }
            for (String s : ret) {
                if (isInline) {
                    result.addElement(LookupElementDecorator.withInsertHandler(LookupElementBuilder.create(s), new InlineInsertHandler()));
                }
                else {
                    result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.INSERT_SPACE));
                }
            }
            result.stopHere(); // no word completions at this point
        }

        @RequiredReadAction
        private static void addSpecialTags(List<String> result, PsiDocComment comment, String tagName) {
            switch (tagName) {
                case "author" -> result.add(tagName + " " + Platform.current().user().name());

                case "param" -> {
                    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(comment, PsiMethod.class);
                    if (psiMethod != null) {
                        PsiDocTag[] tags = comment.getTags();
                        for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
                            if (!JavaDocLocalInspection.isFound(tags, param)) {
                                result.add(tagName + " " + param.getName());
                            }
                        }
                    }
                }

                case "see" -> {
                    if (PsiTreeUtil.getParentOfType(comment, PsiMember.class) instanceof PsiClass psiClass) {
                        InheritanceUtil.processSupers(
                            psiClass,
                            false,
                            thisClass -> {
                                String name = thisClass.getQualifiedName();
                                if (StringUtil.isNotEmpty(name) && !CommonClassNames.JAVA_LANG_OBJECT.equals(name)) {
                                    result.add("see " + name);
                                }
                                return true;
                            }
                        );
                    }
                }
            }
        }
    }

    private static class InlineInsertHandler implements InsertHandler<LookupElement> {
        @Override
        @RequiredUIAccess
        public void handleInsert(InsertionContext context, LookupElement item) {
            if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) {
                return;
            }

            Project project = context.getProject();
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            Editor editor = context.getEditor();
            CaretModel caretModel = editor.getCaretModel();
            int offset = caretModel.getOffset();
            PsiElement element = context.getFile().findElementAt(offset - 1);
            PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
            assert tag != null;

            for (PsiElement child = tag.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof PsiDocToken token && token.getTokenType() == JavaDocTokenType.DOC_INLINE_TAG_END) {
                    return;
                }
            }

            String name = tag.getName();

            CharSequence chars = editor.getDocument().getCharsSequence();
            int currentOffset = caretModel.getOffset();
            if (chars.charAt(currentOffset) == '}') {
                caretModel.moveToOffset(offset + 1);
            }
            else if (chars.charAt(currentOffset + 1) == '}' && chars.charAt(currentOffset) == ' ') {
                caretModel.moveToOffset(offset + 2);
            }
            else if (name.equals(LINK_TAG)) {
                EditorModificationUtil.insertStringAtCaret(editor, " }");
                caretModel.moveToOffset(offset + 1);
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().removeSelection();
            }
            else {
                EditorModificationUtil.insertStringAtCaret(editor, "}");
                caretModel.moveToOffset(offset + 1);
            }
        }
    }

    private static class MethodSignatureInsertHandler implements InsertHandler<JavaMethodCallElement> {
        @Override
        @RequiredUIAccess
        public void handleInsert(InsertionContext context, JavaMethodCallElement item) {
            PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
            Editor editor = context.getEditor();
            PsiMethod method = item.getObject();

            PsiParameter[] parameters = method.getParameterList().getParameters();
            StringBuilder buffer = new StringBuilder();

            CharSequence chars = editor.getDocument().getCharsSequence();
            int endOffset = editor.getCaretModel().getOffset();
            Project project = context.getProject();
            int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
            int signatureOffset = afterSharp;

            PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
            CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
            PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
            if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR && tag != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null) {
                    endOffset = valueElement.getTextRange().getEndOffset();
                    context.setTailOffset(endOffset);
                }
            }
            editor.getDocument().deleteString(afterSharp, endOffset);
            editor.getCaretModel().moveToOffset(signatureOffset);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().removeSelection();
            buffer.append(method.getName()).append("(");
            int afterParen = afterSharp + buffer.length();
            for (int i = 0; i < parameters.length; i++) {
                PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
                buffer.append(type.getCanonicalText());

                if (i < parameters.length - 1) {
                    buffer.append(",");
                    if (styleSettings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA) {
                        buffer.append(" ");
                    }
                }
            }
            buffer.append(")");
            if (!(tag instanceof PsiInlineDocTag)) {
                buffer.append(" ");
            }
            else {
                int currentOffset = editor.getCaretModel().getOffset();
                if (chars.charAt(currentOffset) == '}') {
                    afterSharp++;
                }
                else {
                    buffer.append("} ");
                }
            }
            String insertString = buffer.toString();
            EditorModificationUtil.insertStringAtCaret(editor, insertString);
            editor.getCaretModel().moveToOffset(afterSharp + buffer.length());
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

            shortenReferences(project, editor, context, afterParen);
        }

        @RequiredReadAction
        private static void shortenReferences(Project project, Editor editor, InsertionContext context, int offset) {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiElement element = context.getFile().findElementAt(offset);
            PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
            if (!JavaDocUtil.isInsidePackageInfo(docComment)) {
                PsiDocTagValue tagValue = PsiTreeUtil.getParentOfType(element, PsiDocTagValue.class);
                if (tagValue != null) {
                    try {
                        JavaCodeStyleManager.getInstance(project).shortenClassReferences(tagValue);
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
                PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
            }
        }
    }
}
