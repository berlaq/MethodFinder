package com.methodfinder.methodfinder;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

public class MethodFinder extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String className = findClassName(e);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();
        assert editor != null;
        CaretModel caretModel = editor.getCaretModel();
        LogicalPosition initialPosition = caretModel.getLogicalPosition();

        assert project != null;
        PsiClass psiClass = PsiShortNamesCache.getInstance(project).getClassesByName(className, GlobalSearchScope.everythingScope(project))[0];
        Map<String, PsiMethod> methods = Arrays.stream(psiClass.getMethods())
                .filter(method -> method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC))
                .collect(Collectors.toMap(name -> name.getName() + name.getParameterList().getText() , method -> method));

        JBPopupFactory popupFactory = JBPopupFactory.getInstance();
        popupFactory.createPopupChooserBuilder(new ArrayList<>(methods.keySet()))
                .setTitle("Available Public Methods")
                .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                .setItemChosenCallback((chosen) -> WriteCommandAction.runWriteCommandAction(project, () -> {
                    String sentence = buildSentence(methods.get(chosen));
                    editor.getDocument().insertString(caretModel.getOffset(), sentence);
                    LogicalPosition logicalPosition = new LogicalPosition(initialPosition.line, initialPosition.column + sentence.length());
                    CaretState caretState = new CaretState(logicalPosition, null, null);
                    editor.getCaretModel().setCaretsAndSelections(List.of(caretState));
                }))
                .createPopup()
                .showInBestPositionFor(e.getDataContext());
    }

    private String buildSentence(PsiMethod method) {
        return '"' + method.getName() + '"' +
                ", " + buildReturnType(method);
    }

    private String buildReturnType(PsiMethod method) {
        String presentableText = Objects.requireNonNull(method.getReturnType()).getPresentableText();
        if (presentableText.contains("<")) {
            return presentableText.substring(0, presentableText.indexOf("<"))+".class, ";
        } else {
            return presentableText+".class,".trim()+" ";
        }
    }

    private String findClassName(AnActionEvent event) {
        Editor editor = PlatformDataKeys.EDITOR.getData(event.getDataContext());
        assert editor != null;
        CaretModel caretModel = editor.getCaretModel();
        LogicalPosition logicalPosition = caretModel.getLogicalPosition();

        Document document = editor.getDocument();
        int lineStartOffset = document.getLineStartOffset(logicalPosition.line);
        int lineEndOffset = document.getLineEndOffset(logicalPosition.line);

        TextRange textRange = new TextRange(lineStartOffset, lineEndOffset);
        String text = document.getText(textRange);
        return text.substring(text.lastIndexOf("(", logicalPosition.column)+1, text.lastIndexOf(".", logicalPosition.column));
    }
}
