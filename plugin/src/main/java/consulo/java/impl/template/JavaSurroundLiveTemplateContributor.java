package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaSurroundLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "javasurround";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Java Surround");
    }

    @Override
    public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
//        try (Builder builder = factory.newBuilder("javasurroundB", "B", "{$SELECTION$}", CodeInsightLocalize.livetemplateDescriptionSurroundBraces())) {
//            builder.withReformat();
//
//            builder.withOption(ShortenFQNamesProcessor.KEY, true);
//
//            builder.withVariable("SELECTION", "", "", false);
//
//            builder.withContext(JavaGenericContextType.class, false);
//            builder.withContext(JavaCommentContextType.class, false);
//            builder.withContext(JavaStringContextType.class, false);
//            builder.withContext(XML.class, false);
//            builder.withContext(HTML.class, false);
//            builder.withContext(JSP.class, false);
//            builder.withContext(SmartCompletionContextType.class, false);
//            builder.withContext(GROOVY.class, false);
//            builder.withContext(GROOVY_STATEMENT.class, false);
//            builder.withContext(OTHER.class, true);
//        }
//
//        try (Builder builder = factory.newBuilder("javasurroundP", "P", "($SELECTION$)", CodeInsightLocalize.livetemplateDescriptionSurroundParens())) {
//            builder.withOption(ShortenFQNamesProcessor.KEY, true);
//
//            builder.withVariable("SELECTION", "", "", false);
//
//            builder.withContext(JavaGenericContextType.class, false);
//            builder.withContext(JavaCommentContextType.class, false);
//            builder.withContext(JavaStringContextType.class, false);
//            builder.withContext(XML.class, false);
//            builder.withContext(HTML.class, false);
//            builder.withContext(JSP.class, false);
//            builder.withContext(SmartCompletionContextType.class, false);
//            builder.withContext(GROOVY.class, false);
//            builder.withContext(GROOVY_EXPRESSION.class, false);
//            builder.withContext(OTHER.class, true);
//        }

        try (Builder builder = factory.newBuilder("javasurroundC", "C", "java.util.concurrent.Callable<$RET$> callable = new java.util.concurrent.Callable<$RET$>() {\n"
            + "  public $RET$ call() throws Exception {\n"
            + "    $SELECTION$\n"
            + "    $END$ \n"
            + "  }\n"
            + "};", CodeInsightLocalize.livetemplateDescriptionSurroundWithCallable())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("RET", "", "\"java.lang.Object\"", true);
            builder.withVariable("SELECTION", "", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javasurroundRL", "RL", "$LOCK$.readLock().lock();\n"
            + "try { \n"
            + "  $SELECTION$\n"
            + "} finally {\n"
            + "  $LOCK$.readLock().unlock();\n"
            + "}\n", CodeInsightLocalize.livetemplateDescriptionSurroundWithReadLock())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("LOCK", "variableOfType(\"java.util.concurrent.locks.ReadWriteLock\")", "", true);
            builder.withVariable("SELECTION", "", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javasurroundWL", "WL", "$LOCK$.writeLock().lock();\n"
            + "try { \n"
            + "  $SELECTION$\n"
            + "} finally {\n"
            + "  $LOCK$.writeLock().unlock();\n"
            + "}\n", CodeInsightLocalize.livetemplateDescriptionSurroundWithWriteLock())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("LOCK", "variableOfType(\"java.util.concurrent.locks.ReadWriteLock\")", "", true);
            builder.withVariable("SELECTION", "", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javasurroundI", "I", "for ($ELEMENT_TYPE$ $VAR$ : $SELECTION$) {\n"
            + "  $END$\n"
            + "}\n", CodeInsightLocalize.livetemplateDescriptionIter())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("SELECTION", "", "", false);
            builder.withVariable("ELEMENT_TYPE", "iterableComponentType(SELECTION)", "\"java.lang.Object\"", false);
            builder.withVariable("VAR", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

    }
}
