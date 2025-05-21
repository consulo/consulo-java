// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.ASTFactory;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A helper class to implement quick-fix which collects removed comments from the PSI and can restore them at once.
 * <p>
 * After this object restores comments, it becomes unusable.
 *
 * @author Tagir Valeev
 */
public final class CommentTracker {
    private final Set<PsiElement> ignoredParents = new HashSet<>();
    private List<PsiComment> comments = new ArrayList<>();
    private PsiElement lastTextWithCommentsElement = null;

    /**
     * Marks the element as unchanged and returns its text. The unchanged elements are assumed to be preserved
     * in the resulting code as is, so the comments from them will not be extracted.
     *
     * @param element element to return the text
     * @return a text to be inserted into refactored code
     */
    @Nonnull
    @RequiredReadAction
    public String text(@Nonnull PsiElement element) {
        checkState();
        addIgnored(element);
        return element.getText();
    }

    /**
     * Marks the expression as unchanged and returns its text, adding parentheses if necessary.
     * The unchanged elements are assumed to be preserved in the resulting code as is,
     * so the comments from them will not be extracted.
     *
     * @param element    expression to return the text
     * @param precedence precedence of surrounding operation
     * @return a text to be inserted into refactored code
     * @see ParenthesesUtils#getText(PsiExpression, int)
     */
    @Nonnull
    public String text(@Nonnull PsiExpression element, int precedence) {
        checkState();
        addIgnored(element);
        return ParenthesesUtils.getText(element, precedence + 1);
    }

    /**
     * Marks the expression as unchanged and returns a single-parameter lambda text which parameter
     * is the name of supplied variable and body is the supplied expression
     *
     * @param variable   a variable to use as lambda parameter
     * @param expression an expression to use as lambda body
     * @return a string representation of lambda
     */
    @Nonnull
    @RequiredReadAction
    public String lambdaText(@Nonnull PsiVariable variable, @Nonnull PsiExpression expression) {
        return variable.getName() + " -> " + text(expression);
    }

    /**
     * Marks the element as unchanged and returns it. The unchanged elements are assumed to be preserved
     * in the resulting code as is, so the comments from them will not be extracted.
     *
     * @param element element to mark
     * @param <T>     the type of the element
     * @return the passed argument
     */
    @Contract("_ -> param1")
    public <T extends PsiElement> T markUnchanged(@Nullable T element) {
        checkState();
        if (element != null) {
            addIgnored(element);
        }
        return element;
    }

    /**
     * Marks the range of elements as unchanged and returns their text. The unchanged elements are assumed to be preserved
     * in the resulting code as is, so the comments from them will not be extracted.
     *
     * @param firstElement first element to mark
     * @param lastElement  last element to mark (must be equal to firstElement or its sibling)
     * @return a text to be inserted into refactored code
     * @throws IllegalArgumentException if firstElement and lastElements are not siblings or firstElement goes after last element
     */
    @RequiredReadAction
    public String rangeText(@Nonnull PsiElement firstElement, @Nonnull PsiElement lastElement) {
        checkState();
        PsiElement e;
        StringBuilder result = new StringBuilder();
        for (e = firstElement; e != null && e != lastElement; e = e.getNextSibling()) {
            addIgnored(e);
            result.append(e.getText());
        }
        if (e == null) {
            throw new IllegalArgumentException("Elements must be siblings: " + firstElement + " and " + lastElement);
        }
        addIgnored(lastElement);
        result.append(lastElement.getText());
        return result.toString();
    }

    /**
     * Marks the range of elements as unchanged. The unchanged elements are assumed to be preserved
     * in the resulting code as is, so the comments from them will not be extracted.
     *
     * @param firstElement first element to mark
     * @param lastElement  last element to mark (must be equal to firstElement or its sibling)
     * @throws IllegalArgumentException if firstElement and lastElements are not siblings or firstElement goes after last element
     */
    @RequiredReadAction
    public void markRangeUnchanged(@Nonnull PsiElement firstElement, @Nonnull PsiElement lastElement) {
        checkState();
        PsiElement e;
        for (e = firstElement; e != null && e != lastElement; e = e.getNextSibling()) {
            addIgnored(e);
        }
        if (e == null) {
            throw new IllegalArgumentException("Elements must be siblings: " + firstElement + " and " + lastElement);
        }
        addIgnored(lastElement);
    }

    /**
     * Returns the comments which are located between the supplied element
     * and the previous element passed into {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
     * The used comments are deleted from the original document.
     *
     * <p>This method can be used if several parts of original code are reused in the generated replacement.
     *
     * @param element an element grab the comments before it
     * @return the string containing the element text and possibly some comments.
     */
    @RequiredReadAction
    public String commentsBefore(@Nonnull PsiElement element) {
        List<PsiElement> comments = grabCommentsBefore(element);
        if (comments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PsiElement comment : comments) {
            PsiElement prev = comment.getPrevSibling();
            if (sb.length() == 0 && prev instanceof PsiWhiteSpace whiteSpace) {
                sb.append(whiteSpace.getText());
            }
            sb.append(comment.getText());
            PsiElement next = PsiTreeUtil.nextLeaf(comment);
            if (next instanceof PsiWhiteSpace whiteSpace) {
                sb.append(whiteSpace.getText());
            }
        }
        comments.forEach(PsiElement::delete);
        return sb.toString();
    }

    @RequiredReadAction
    private List<PsiElement> grabCommentsBefore(@Nonnull PsiElement element) {
        if (lastTextWithCommentsElement == null) {
            lastTextWithCommentsElement = element;
            return Collections.emptyList();
        }
        List<PsiElement> result = new SmartList<>();
        int start = lastTextWithCommentsElement.getTextRange().getEndOffset();
        int end = element.getTextRange().getStartOffset();
        PsiElement parent = PsiTreeUtil.findCommonParent(lastTextWithCommentsElement, element);
        if (parent != null && start < end) {
            PsiTreeUtil.processElements(
                parent,
                e -> {
                    if (e instanceof PsiComment comment) {
                        TextRange range = comment.getTextRange();
                        if (range.getStartOffset() >= start && range.getEndOffset() <= end && !shouldIgnore(comment)) {
                            result.add(comment);
                        }
                    }
                    return true;
                }
            );
        }

        lastTextWithCommentsElement = element;
        return result;
    }

    /**
     * Returns an element text, possibly prepended with comments which are located between the supplied element
     * and the previous element passed into {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
     * The used comments are deleted from the original document.
     *
     * <p>Note that if PsiExpression was passed, the resulting text may not parse as an PsiExpression,
     * because PsiExpression cannot start with comment.
     *
     * <p>This method can be used if several parts of original code are reused in the generated replacement.
     *
     * @param element an element to convert to the text
     * @return the string containing the element text and possibly some comments.
     */
    @RequiredReadAction
    public String textWithComments(@Nonnull PsiElement element) {
        return commentsBefore(element) + element.getText();
    }

    /**
     * Returns an element text, adding parentheses if necessary, possibly prepended with comments which are
     * located between the supplied element and the previous element passed into
     * {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
     * The used comments are deleted from the original document.
     *
     * <p>Note that if PsiExpression was passed, the resulting text may not parse as an PsiExpression,
     * because PsiExpression cannot start with comment.
     *
     * <p>This method can be used if several parts of original code are reused in the generated replacement.
     *
     * @param expression an expression to convert to the text
     * @param precedence precedence of surrounding operation
     * @return the string containing the element text and possibly some comments.
     */
    @RequiredReadAction
    public String textWithComments(@Nonnull PsiExpression expression, int precedence) {
        return commentsBefore(expression) + ParenthesesUtils.getText(expression, precedence + 1);
    }

    /**
     * Deletes given PsiElement collecting all the comments inside it.
     *
     * @param element element to delete
     */
    @RequiredWriteAction
    public void delete(@Nonnull PsiElement element) {
        grabCommentsOnDelete(element);
        element.delete();
    }

    /**
     * Deletes all given PsiElement's collecting all the comments inside them.
     *
     * @param elements elements to delete (all not null)
     */
    @RequiredWriteAction
    public void delete(@Nonnull PsiElement... elements) {
        for (PsiElement element : elements) {
            delete(element);
        }
    }

    /**
     * Deletes given PsiElement replacing it with the comments including comments inside the deleted element
     * and previously gathered comments.
     *
     * <p>After calling this method the tracker cannot be used anymore.</p>
     *
     * @param element element to delete
     */
    @RequiredWriteAction
    public void deleteAndRestoreComments(@Nonnull PsiElement element) {
        grabCommentsOnDelete(element);
        PsiElement anchor = element;
        while (anchor.getParent() != null && !(anchor.getParent() instanceof PsiFile) && anchor.getParent().getFirstChild() == anchor) {
            anchor = anchor.getParent();
        }
        insertCommentsBefore(anchor);
        element.delete();
    }

    /**
     * Replaces given PsiElement collecting all the comments inside it.
     *
     * @param element     element to replace
     * @param replacement replacement element. It's also marked as unchanged (see {@link #markUnchanged(PsiElement)})
     * @return the element which was actually inserted in the tree (either {@code replacement} or its copy)
     */
    @Nonnull
    @RequiredWriteAction
    public PsiElement replace(@Nonnull PsiElement element, @Nonnull PsiElement replacement) {
        markUnchanged(replacement);
        grabComments(element);
        return element.replace(replacement);
    }

    /**
     * Creates a replacement element from the text and replaces given element,
     * collecting all the comments inside it.
     *
     * <p>
     * The type of the created replacement will mimic the type of supplied element.
     * Supported element types are: {@link PsiExpression}, {@link PsiStatement},
     * {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
     * </p>
     *
     * @param element element to replace
     * @param text    replacement text
     * @return the element which was actually inserted in the tree
     */
    @Nonnull
    @RequiredWriteAction
    public PsiElement replace(@Nonnull PsiElement element, @Nonnull String text) {
        PsiElement replacement = createElement(element, text);
        return replace(element, replacement);
    }

    /**
     * Replaces given PsiElement collecting all the comments inside it and restores comments putting them
     * to the appropriate place before replaced element.
     *
     * <p>After calling this method the tracker cannot be used anymore.</p>
     *
     * @param element     element to replace
     * @param replacement replacement element. It's also marked as unchanged (see {@link #markUnchanged(PsiElement)})
     * @return the element which was actually inserted in the tree (either {@code replacement} or its copy)
     */
    @Nonnull
    @RequiredWriteAction
    public PsiElement replaceAndRestoreComments(@Nonnull PsiElement element, @Nonnull PsiElement replacement) {
        List<PsiElement> suffix = grabSuffixComments(element);
        PsiElement result = replace(element, replacement);
        PsiElement anchor = PsiTreeUtil
            .getNonStrictParentOfType(result, PsiStatement.class, PsiLambdaExpression.class, PsiVariable.class, PsiNameValuePair.class);
        if (anchor instanceof PsiLambdaExpression lambda && anchor != result) {
            anchor = lambda.getBody();
        }
        if (anchor instanceof PsiVariable variable && variable.getParent() instanceof PsiDeclarationStatement declaration) {
            anchor = declaration;
        }
        if (anchor instanceof PsiStatement && (anchor.getParent() instanceof PsiIfStatement || anchor.getParent() instanceof PsiLoopStatement)) {
            anchor = anchor.getParent();
        }
        if (anchor == null) {
            anchor = result;
        }
        restoreSuffixComments(result, suffix);
        insertCommentsBefore(anchor);
        return result;
    }

    /**
     * Replaces the specified expression and restores any comments to their appropriate place before and/or after the expression.
     * Meant to be used with {@link #commentsBefore(PsiElement)} and {@link #commentsBetween(PsiElement, PsiElement)}
     *
     * @param expression      the expression to replace
     * @param replacementText text of the replacement expression
     * @return the element which was inserted in the tree
     */
    @Nonnull
    @RequiredWriteAction
    public PsiElement replaceExpressionAndRestoreComments(@Nonnull PsiExpression expression, @Nonnull String replacementText) {
        return replaceExpressionAndRestoreComments(expression, replacementText, Collections.emptyList());
    }

    @Nonnull
    @RequiredWriteAction
    public PsiElement replaceExpressionAndRestoreComments(
        @Nonnull PsiExpression expression,
        @Nonnull String replacementText,
        List<? extends PsiElement> toDelete
    ) {
        List<PsiElement> trailingComments = new SmartList<>();
        List<PsiElement> comments = grabCommentsBefore(PsiTreeUtil.lastChild(expression));
        if (!comments.isEmpty()) {
            PsiParserFacade parser = PsiParserFacade.SERVICE.getInstance(expression.getProject());
            for (PsiElement comment : comments) {
                if (comment.getPrevSibling() instanceof PsiWhiteSpace whiteSpace) {
                    String text = whiteSpace.getText();
                    if (!text.contains("\n")) {
                        trailingComments.add(parser.createWhiteSpaceFromText(" "));
                    }
                    else if (text.endsWith("\n")) {
                        // comment at first column
                        trailingComments.add(parser.createWhiteSpaceFromText("\n"));
                    }
                    else {
                        // newline followed by space will cause formatter to indent
                        trailingComments.add(parser.createWhiteSpaceFromText("\n "));
                    }
                }
                ignoredParents.add(comment);
                trailingComments.add(comment.copy());
            }
            Collections.reverse(trailingComments);
        }
        PsiElement replacement = replace(expression, replacementText);
        for (PsiElement element : trailingComments) {
            replacement.getParent().addAfter(element, replacement);
        }
        toDelete.forEach(this::delete);
        insertCommentsBefore(replacement);
        return replacement;
    }

    @Nonnull
    @RequiredReadAction
    private List<PsiElement> grabSuffixComments(@Nonnull PsiElement element) {
        if (!(element instanceof PsiStatement)) {
            return Collections.emptyList();
        }
        List<PsiElement> suffix = new ArrayList<>();
        PsiElement lastChild = element.getLastChild();
        boolean hasComment = false;
        while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
            hasComment |= lastChild instanceof PsiComment;
            if (!(lastChild instanceof PsiComment comment) || !(shouldIgnore(comment))) {
                suffix.add(markUnchanged(lastChild).copy());
            }
            lastChild = lastChild.getPrevSibling();
        }
        return hasComment ? suffix : Collections.emptyList();
    }

    @RequiredReadAction
    private static void restoreSuffixComments(PsiElement target, List<? extends PsiElement> suffix) {
        if (!suffix.isEmpty()) {
            PsiElement lastChild = target.getLastChild();
            if (lastChild instanceof PsiComment comment && JavaTokenType.END_OF_LINE_COMMENT.equals(comment.getTokenType())) {
                if (target.getNextSibling() instanceof PsiWhiteSpace whiteSpace) {
                    target.add(whiteSpace);
                }
                else {
                    target.add(PsiParserFacade.SERVICE.getInstance(target.getProject()).createWhiteSpaceFromText("\n"));
                }
            }
            StreamEx.ofReversed(suffix).forEach(target::add);
        }
    }

    /**
     * Creates a replacement element from the text and replaces given element,
     * collecting all the comments inside it and restores comments putting them
     * to the appropriate place before replaced element.
     *
     * <p>After calling this method the tracker cannot be used anymore.</p>
     *
     * <p>
     * The type of the created replacement will mimic the type of supplied element.
     * Supported element types are: {@link PsiExpression}, {@link PsiStatement},
     * {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
     * </p>
     *
     * @param element element to replace
     * @param text    replacement text
     * @return the element which was actually inserted in the tree
     */
    @Nonnull
    @RequiredWriteAction
    public PsiElement replaceAndRestoreComments(@Nonnull PsiElement element, @Nonnull String text) {
        PsiElement replacement = createElement(element, text);
        return replaceAndRestoreComments(element, replacement);
    }

    @Nonnull
    private static PsiElement createElement(@Nonnull PsiElement element, @Nonnull String text) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        if (element instanceof PsiExpression) {
            return factory.createExpressionFromText(text, element);
        }
        else if (element instanceof PsiStatement) {
            return factory.createStatementFromText(text, element);
        }
        else if (element instanceof PsiTypeElement) {
            return factory.createTypeElementFromText(text, element);
        }
        else if (element instanceof PsiIdentifier) {
            return factory.createIdentifier(text);
        }
        else if (element instanceof PsiComment) {
            return factory.createCommentFromText(text, element);
        }
        else {
            throw new IllegalArgumentException("Unsupported element type: " + element);
        }
    }

    /**
     * Inserts gathered comments just before given anchor element
     *
     * <p>After calling this method the tracker cannot be used anymore.</p>
     *
     * @param anchor element to insert comments before
     */
    @RequiredWriteAction
    public void insertCommentsBefore(@Nonnull PsiElement anchor) {
        checkState();
        if (!comments.isEmpty()) {
            PsiElement parent = anchor.getParent();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
            for (PsiComment comment : comments) {
                if (shouldIgnore(comment)) {
                    continue;
                }
                PsiElement added = parent.addBefore(factory.createCommentFromText(comment.getText(), anchor), anchor);
                if (added.getPrevSibling() instanceof PsiWhiteSpace prevSiblingWhiteSpace) {
                    PsiElement prev = anchor.getPrevSibling();
                    ASTNode whiteSpaceBefore = normalizeWhiteSpace(prevSiblingWhiteSpace, prev);
                    parent.getNode().addChild(whiteSpaceBefore, anchor.getNode());
                    if (prev instanceof PsiWhiteSpace whiteSpace) {
                        whiteSpace.delete();
                    }
                }
            }
        }
        comments = null;
    }

    @Nonnull
    @RequiredReadAction
    private static ASTNode normalizeWhiteSpace(PsiWhiteSpace whiteSpace, PsiElement nextElement) {
        String text = whiteSpace.getText();
        int endLPos = text.lastIndexOf('\n');
        if (text.lastIndexOf('\n', endLPos - 1) >= 0) {
            // has at least two line breaks
            return ASTFactory.whitespace(text.substring(endLPos));
        }
        if (nextElement instanceof PsiWhiteSpace && nextElement.getText().contains("\n") && !text.contains("\n")) {
            text = '\n' + text;
        }
        return ASTFactory.whitespace(text);
    }

    private boolean shouldIgnore(PsiComment comment) {
        return ignoredParents.stream().anyMatch(p -> PsiTreeUtil.isAncestor(p, comment, false));
    }

    @RequiredReadAction
    private void grabCommentsOnDelete(PsiElement element) {
        PsiElement parent = element.getParent();
        if (element instanceof PsiExpression && parent instanceof PsiExpressionStatement
            || (parent instanceof PsiDeclarationStatement declarationStmt && declarationStmt.getDeclaredElements().length == 1)) {
            element = parent;
        }
        else if (parent instanceof PsiJavaCodeReferenceElement parentCodeRefElem) {
            if (element instanceof PsiJavaCodeReferenceElement codeRefElem && codeRefElem.getQualifier() == element) {
                ASTNode dot = ((CompositeElement)parentCodeRefElem).findChildByRole(ChildRole.DOT);
                if (dot != null) {
                    PsiElement nextSibling = dot.getPsi().getNextSibling();
                    if (nextSibling != null && nextSibling.getTextLength() == 0) {
                        nextSibling = PsiTreeUtil.skipSiblingsForward(nextSibling, WS_COMMENTS);
                    }
                    while (nextSibling != null) {
                        nextSibling = markUnchanged(nextSibling).getNextSibling();
                    }
                }
            }
            element = parentCodeRefElem;
        }
        grabComments(element);
    }

    @SuppressWarnings("unchecked")
    private static final Class<? extends PsiElement>[]
        WS_COMMENTS = new Class[]{
        PsiWhiteSpace.class,
        PsiComment.class
    };

    @Deprecated
    @Contract("null -> null")
    @Nullable
    @RequiredReadAction
    public static PsiElement skipWhitespacesAndCommentsForward(@Nullable PsiElement element) {
        return PsiTreeUtil.skipSiblingsForward(element, WS_COMMENTS);
    }

    /**
     * Grab the comments from given element which should be restored. Normally you don't need to call this method.
     * It should be called only if element is about to be deleted by other code which is not CommentTracker-aware.
     *
     * <p>Calling this method repeatedly has no effect. It's also safe to call this method, then delete element using
     * other methods from this class like {@link #delete(PsiElement)}.
     *
     * @param element element to grab the comments from.
     */
    @RequiredReadAction
    public void grabComments(PsiElement element) {
        checkState();
        for (PsiComment comment : PsiTreeUtil.collectElementsOfType(element, PsiComment.class)) {
            if (!shouldIgnore(comment)) {
                comments.add(comment);
            }
        }
    }

    private void checkState() {
        if (comments == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " has been already used");
        }
    }

    private void addIgnored(PsiElement element) {
        if (!(element instanceof LeafPsiElement) || element instanceof PsiComment) {
            ignoredParents.add(element);
        }
    }

    @RequiredReadAction
    public static String textWithSurroundingComments(PsiElement element) {
        Predicate<PsiElement> commentOrWhiteSpace = e -> e instanceof PsiComment || e instanceof PsiWhiteSpace;
        List<PsiElement> prev = StreamEx.iterate(element.getPrevSibling(), commentOrWhiteSpace, PsiElement::getPrevSibling).toList();
        List<PsiElement> next = StreamEx.iterate(element.getNextSibling(), commentOrWhiteSpace, PsiElement::getNextSibling).toList();
        if (StreamEx.of(prev, next).flatCollection(Function.identity()).anyMatch(PsiComment.class::isInstance)) {
            return StreamEx.ofReversed(prev).append(element).append(next).map(PsiElement::getText).joining();
        }
        return element.getText();
    }

    /**
     * Returns a string containing all the comments (possibly with some white-spaces) between given elements
     * (not including given elements themselves). This method also deletes all the comments actually used
     * in the returned string.
     *
     * @param start start element
     * @param end   end element, must strictly follow the start element and be located in the same file
     *              (though possibly on another hierarchy level)
     * @return a string containing all the comments between start and end.
     */
    @Nonnull
    @RequiredReadAction
    public static String commentsBetween(@Nonnull PsiElement start, @Nonnull PsiElement end) {
        CommentTracker ct = new CommentTracker();
        ct.lastTextWithCommentsElement = start;
        return ct.commentsBefore(end);
    }
}