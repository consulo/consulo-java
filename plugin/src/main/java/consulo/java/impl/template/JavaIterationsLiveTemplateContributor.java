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
public class JavaIterationsLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "javaiterations";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Java Iterations");
    }

    @Override
    public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder("javaiterationsFori", "fori", "for(int $INDEX$ = 0; $INDEX$ < $LIMIT$; $INDEX$++) {\n"
            + "  $END$\n"
            + "}", LocalizeValue.localizeTODO("Create iteration loop"))) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("INDEX", "suggestIndexName()", "", true);
            builder.withVariable("LIMIT", "", "", true);

            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsItar", "itar", "for(int $INDEX$ = 0; $INDEX$ < $ARRAY$.length; $INDEX$++) {\n"
            + "  $ELEMENT_TYPE$ $VAR$ = $ARRAY$[$INDEX$];\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionItar())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("INDEX", "suggestIndexName()", "", true);
            builder.withVariable("ARRAY", "arrayVariable()", "\"array\"", true);
            builder.withVariable("ELEMENT_TYPE", "componentTypeOf(ARRAY)", "", false);
            builder.withVariable("VAR", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsItco", "itco", "for($ITER_TYPE$ $ITER$ = $COLLECTION$.iterator(); $ITER$.hasNext(); ) {\n"
            + "  $ELEMENT_TYPE$ $VAR$ =$CAST$ $ITER$.next();\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionItco())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ITER", "suggestVariableName()", "", true);
            builder.withVariable("COLLECTION", "variableOfType(\"java.util.Collection\")", "\"collection\"", true);
            builder.withVariable("ELEMENT_TYPE", "guessElementType(COLLECTION)", "\"Object\"", true);
            builder.withVariable("VAR", "suggestVariableName()", "", true);
            builder.withVariable("ITER_TYPE", "rightSideType()", "\"java.util.Iterator\"", false);
            builder.withVariable("CAST", "castToLeftSideType()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsIten", "iten", "while($ENUM$.hasMoreElements()){\n"
            + "  $TYPE$ $VAR$ = $CAST$ $ENUM$.nextElement();\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionIten())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ENUM", "variableOfType(\"java.util.Enumeration\")", "\"enumeration\"", true);
            builder.withVariable("TYPE", "rightSideType()", "\"Object\"", true);
            builder.withVariable("VAR", "suggestVariableName()", "", true);
            builder.withVariable("CAST", "castToLeftSideType()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsItit", "itit", "while($ITER$.hasNext()){\n"
            + "  $TYPE$ $VAR$ = $CAST$ $ITER$.next();\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionItit())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ITER", "variableOfType(\"java.util.Iterator\")", "\"iterator\"", true);
            builder.withVariable("TYPE", "rightSideType()", "\"Object\"", true);
            builder.withVariable("VAR", "suggestVariableName()", "", true);
            builder.withVariable("CAST", "castToLeftSideType()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsItli", "itli", "for (int $INDEX$ = 0; $INDEX$ < $LIST$.size(); $INDEX$++) {\n"
            + "  $ELEMENT_TYPE$ $VAR$ = $CAST$ $LIST$.get($INDEX$);\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionItli())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("INDEX", "suggestIndexName()", "", true);
            builder.withVariable("LIST", "variableOfType(\"java.util.List\")", "\"list\"", true);
            builder.withVariable("ELEMENT_TYPE", "guessElementType(LIST)", "\"Object\"", true);
            builder.withVariable("VAR", "suggestVariableName()", "\"o\"", true);
            builder.withVariable("CAST", "castToLeftSideType()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsIttok", "ittok", "for (java.util.StringTokenizer $TOKENIZER$ = new java.util.StringTokenizer($STRING$); $TOKENIZER$.hasMoreTokens(); ) {\n"
            + "    String $VAR$ = $TOKENIZER_COPY$.nextToken();\n"
            + "    $END$\n"
            + "}\n", CodeInsightLocalize.livetemplateDescriptionIttok())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("TOKENIZER", "suggestVariableName()", "\"tokenizer\"", true);
            builder.withVariable("STRING", "variableOfType(\"java.lang.String\")", "", true);
            builder.withVariable("VAR", "suggestVariableName()", "\"token\"", true);
            builder.withVariable("TOKENIZER_COPY", "TOKENIZER  ", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsItve", "itve", "for(int $INDEX$ = 0; $INDEX$ < $VECTOR$.size(); $INDEX$++) {\n"
            + "  $ELEMENT_TYPE$ $VAR$ = $CAST$ $VECTOR$.elementAt($INDEX$);\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionItve())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("INDEX", "suggestIndexName()", "", true);
            builder.withVariable("VECTOR", "variableOfType(\"java.util.Vector\")", "\"vector\"", true);
            builder.withVariable("ELEMENT_TYPE", "guessElementType(VECTOR)", "\"Object\"", true);
            builder.withVariable("VAR", "suggestVariableName()", "", true);
            builder.withVariable("CAST", "castToLeftSideType()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsRitar", "ritar", "for(int $INDEX$ = $ARRAY$.length - 1; $INDEX$ >= 0; $INDEX$--) {\n"
            + "  $ELEMENT_TYPE$ $VAR$ = $ARRAY$[$INDEX$];\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionRitar())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("INDEX", "suggestIndexName()", "", true);
            builder.withVariable("ARRAY", "arrayVariable()", "\"array\"", true);
            builder.withVariable("ELEMENT_TYPE", "componentTypeOf(ARRAY)", "", false);
            builder.withVariable("VAR", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaiterationsIter", "iter", "for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {\n"
            + "  $END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionIter())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ITERABLE_TYPE", "iterableVariable()", "", true);
            builder.withVariable("ELEMENT_TYPE", "iterableComponentType(ITERABLE_TYPE)", "\"java.lang.Object\"", false);
            builder.withVariable("VAR", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

    }
}