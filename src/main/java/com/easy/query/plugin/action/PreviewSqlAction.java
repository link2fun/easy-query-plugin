package com.easy.query.plugin.action;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.easy.query.plugin.core.util.MyModuleUtil;
import com.easy.query.plugin.core.util.PsiJavaFileUtil;
import com.easy.query.plugin.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预览SQL
 *
 * @author link2fun
 */
public class PreviewSqlAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {

        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (StrUtil.isBlank(selectedText)) {
            // 没有选择文字
            return;
        }

        PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiClass psiClassSource = ((PsiJavaFile) psiFile).getClasses()[0];

        PsiJavaFile psiJavaFileSource = (PsiJavaFile) psiFile;

        PsiElement selectedElementRaw = PsiTreeUtil.findElementOfClassAtRange(psiJavaFileSource, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), PsiElement.class);

        // 很可能没选全, 这里获取最外围的方法调用
        PsiElement selectedElements = PsiTreeUtil.getTopmostParentOfType(selectedElementRaw, PsiMethodCallExpression.class);

        if (selectedElements == null || selectedElements.getChildren().length == 0 || selectedElements.getChildren()[0].getChildren().length == 0) {
            return;
        }

        selectedText = selectedElements.getChildren()[0].getChildren()[0].getText();


        List<PsiReferenceExpression> refOrVarList = PsiTreeUtil.findChildrenOfType(selectedElements, PsiReferenceExpression.class).stream()
                .filter(ref -> {
                    PsiElement resolved = ref.resolve();
                    if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
                        // 如果 resolved 的位置, 在选中的内部, 说明是内部定义的, 不是外部引用的
                        if (selectionModel.getSelectionStart() <= resolved.getTextOffset() && resolved.getTextOffset() <= selectionModel.getSelectionEnd()) {
                            return false;
                        }

                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());


        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiFile.getProject());
        // 需要把这些外部变量给定义了
        Set<String> varRegistered = Sets.newHashSet();
        List<String> varList = Lists.newArrayList();
        for (PsiReferenceExpression varRef : refOrVarList) {
            PsiElement varEle = varRef.resolve();
            if (varEle instanceof PsiLocalVariable || varEle instanceof PsiParameter) {
                PsiType type = varEle instanceof PsiLocalVariable ? ((PsiLocalVariable) varEle).getType() : ((PsiParameter) varEle).getType();
                String varName = varEle instanceof PsiLocalVariable ? ((PsiLocalVariable) varEle).getName() : ((PsiParameter) varEle).getName();
                String varType = type.getCanonicalText();
                if (varRegistered.contains(varName)) {
                    continue;
                }
                varRegistered.add(varName);

                // 定义变量
                String varDef;
                if (StrUtil.equalsAny(varType, String.class.getCanonicalName())) {
                    varDef = varType + " " + varName + " = \"" + RandomUtil.randomString(RandomUtil.randomInt(1, 10)) + "\";";
                } else if (StrUtil.equalsAny(varType, Long.class.getCanonicalName())) {
                    varDef = varType + " " + varName + " = " + RandomUtil.randomLong() + "L;";
                } else if (StrUtil.equalsAny(varType, Double.class.getCanonicalName())) {
                    varDef = varType + " " + varName + " = " + RandomUtil.randomDouble() + "d;";
                } else if (StrUtil.equalsAny(varType, Integer.class.getCanonicalName())) {
                    varDef = varType + " " + varName + " = " + RandomUtil.randomInt() + ";";
                }
                // boolean
                else if (StrUtil.equalsAny(varType, Boolean.class.getCanonicalName())) {
                    varDef = varType + " " + varName + " = " + RandomUtil.randomBoolean() + ";";
                }
                // BigDecimal
                else if (StrUtil.equalsAny(varType, "java.math.BigDecimal")) {
                    varDef = varType + " " + varName + " = new " + varType + "(\"" + RandomUtil.randomDouble() + "\");";
                }
                // LocalDate
                else if (StrUtil.equalsAny(varType, "java.time.LocalDate")) {
                    varDef = varType + " " + varName + " = " + varType + ".now();";
                }
                // LocalDateTime
                else if (StrUtil.equalsAny(varType, "java.time.LocalDateTime")) {
                    varDef = varType + " " + varName + " = " + varType + ".now();";
                } else {
                    varDef = varType + " " + varName + " = new " + varType + "();";

                }
                PsiElement varDefEle = elementFactory.createStatementFromText(varDef, psiClassSource);
                String varDefEleText = varDefEle.getText();
                varList.add(varDefEleText);
            }
        }

        Module currentModule = MyModuleUtil.getModuleForFile(psiFile.getProject(), psiFile.getVirtualFile());

        // 拷贝当前文件
        PsiJavaFile psiJavaFileCopied = (PsiJavaFile) psiJavaFileSource.copy();
        // 拷贝类文件
        PsiClass psiClassCopied = (PsiClass) psiClassSource.copy();

        // 修改类文件, 移除接口
        PsiReferenceList implementsList = psiClassCopied.getImplementsList();
        for (PsiJavaCodeReferenceElement referenceElement : implementsList.getReferenceElements()) {
            referenceElement.delete();
        }

        // 添加上新的接口 javax.sql.DataSource
        PsiJavaCodeReferenceElement dataSourceInterface = elementFactory.createReferenceFromText("javax.sql.DataSource", psiClassCopied);
        implementsList.add(dataSourceInterface);


        // 修改类文件, 移除注解
        for (PsiMethod method : psiClassCopied.getMethods()) {
            method.delete();
        }

        // 移除里面的字段
        PsiField[] fields = psiClassCopied.getFields();
        for (PsiField field : fields) {
            field.delete();
        }

        // 移除类上面的注解
        PsiModifierList modifierList = psiClassCopied.getModifierList();
        if (modifierList != null) {
            PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                annotation.delete();
            }
        }

        // 构建一个 Main 方法
        Project project = psiClassCopied.getProject();
        PsiMethod mainMethod = JavaPsiFacade.getElementFactory(project)
                .createMethodFromText("public static void main(String[] args) {\n" +
                        "        System.out.println(\"EasyQuery Preview SQL\");\n" +
                        "\n" +
                        "    // 采用控制台输出\n" +
                        "    LogFactory.useStdOutLogging();\n" +
                        "\n" +
                        "    EasyQueryClient queryClient = EasyQueryBootstrapper.defaultBuilderConfiguration()\n" +
                        "      .setDefaultDataSource(new EasyQueryPreviewSqlAction())\n" +
                        "      .optionConfigure(option -> {\n" +
                        "        option.setPrintSql(true); // 输出SQL\n" +
                        "        option.setKeepNativeStyle(true);\n" +
                        "      })\n" +
                        "      .useDatabaseConfigure(new MsSQLDatabaseConfiguration())\n" +
                        "      .build();\n" +
                        "\n" +
                        "    EasyEntityQuery entityQuery = new DefaultEasyEntityQuery(queryClient);\n" +


                        varList.stream().collect(Collectors.joining("\n")) +

                        "String sql = " + selectedText + ".toSQL();" +
                        "        System.out.println(sql);\n" +


                        // TODO 这里加上全部的代码
                        "    }", psiClassCopied);

        // 添加 Main 方法
        psiClassCopied.add(mainMethod);

        // 为了测试方便, 这里需要把DataSource 的一些接口给实现上
        addDataSourceInterfaceImplement(psiClassCopied, elementFactory);

        handleImport(psiJavaFileCopied, elementFactory, project);


        // TODO 还需要引入外部包

        // TODO 方法中可能引用了 其他实体作为参数

        // 修改下 psiClassCopied 的类名
        psiClassCopied.setName("EasyQueryPreviewSqlAction");
        // 修改下文件名
        psiJavaFileCopied.setName("EasyQueryPreviewSqlAction.java");

        String qualifiedName = psiJavaFileCopied.getPackageName() + "." + psiClassCopied.getName();

        // 替换类
        PsiTreeUtil.getStubChildOfType(psiJavaFileCopied, PsiClass.class).replace(psiClassCopied);

        System.out.println(psiClassCopied.getText());
        System.out.println(psiJavaFileCopied.getText());

        PsiDirectory containingDirectory = psiJavaFileSource.getContainingDirectory();

        WriteCommandAction.runWriteCommandAction(event.getProject(), () -> {

            PsiFile file = containingDirectory.findFile(psiJavaFileCopied.getName());
            if (ObjectUtil.isNotNull(file)) {
                file.delete();
            }
            PsiElement psiElementFormated = containingDirectory.add(CodeStyleManager.getInstance(project).reformat(psiJavaFileCopied));
            VirtualFile virtualFile = psiElementFormated.getContainingFile().getVirtualFile();

            // 编辑器打开这个文件
            FileEditorManager.getInstance(project).openFile(virtualFile, true);

//            // 删除临时文件
//            ApplicationManager.getApplication().invokeLater(() -> {
//                try {
//                    virtualFile.delete(this);
//                } catch (IOException e) {
//                    System.err.println("删除文件失败");
//                }
//            }, ModalityState.defaultModalityState());

            // 编译文件
            CompilerManager compilerManager = CompilerManager.getInstance(project);
            compilerManager.compile(new VirtualFile[]{virtualFile}, new CompileStatusNotification() {
                @Override
                public void finished(boolean aborted, int errors, int warnings,
                                     @NotNull CompileContext compileContext) {
                    if (errors > 0) {
                        System.err.println("编译出错");
                        return;
                    }


                    RunManager runManager = RunManager.getInstance(project);
                    ConfigurationType configurationType = ConfigurationTypeUtil.findConfigurationType(ApplicationConfigurationType.class);
                    ConfigurationFactory configurationFactory = configurationType.getConfigurationFactories()[0];

                    // 获取配置模板
                    RunnerAndConfigurationSettings templateSettings = runManager.getConfigurationTemplate(configurationFactory);

                    RunnerAndConfigurationSettings easyQueryPreviewSqlSettings = runManager.createConfiguration("EasyQuery Preview SQL", templateSettings.getFactory());
                    easyQueryPreviewSqlSettings.setTemporary(true); // 设为临时的
                    ApplicationConfiguration runConfiguration = (ApplicationConfiguration) easyQueryPreviewSqlSettings.getConfiguration();

                    runConfiguration.setMainClassName(qualifiedName);
                    runConfiguration.setModule(currentModule);


                    runManager.addConfiguration(easyQueryPreviewSqlSettings);


                    // 执行配置
                    // 执行配置并获取结果
                    ExecutionEnvironmentBuilder builder;
                    try {
                        builder = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), easyQueryPreviewSqlSettings);
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                        return;
                    }
                    ExecutionEnvironment environment = builder.build();
                    ProgramRunnerUtil.executeConfiguration(environment, true, true);

                }
            });

        });

    }

    /**
     * 处理import
     */
    private static void handleImport(PsiJavaFile psiJavaFileCopied, PsiElementFactory elementFactory, Project project) {
        // 添加EQ相关import
        // import com.easy.query.core.logging.LogFactory
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "com.easy.query.core.logging.LogFactory")));
        // import com.easy.query.core.api.client.EasyQueryClient
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "com.easy.query.core.api.client.EasyQueryClient")));
        // import com.easy.query.core.bootstrapper.EasyQueryBootstrapper
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "com.easy.query.core.bootstrapper.EasyQueryBootstrapper")));
        // import com.easy.query.mssql.config.MsSQLDatabaseConfiguration
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "com.easy.query.mssql.config.MsSQLDatabaseConfiguration")));
        // import java.sql.Connection
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "java.sql.Connection")));
        // import java.sql.SQLException
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "java.sql.SQLException")));
        // import java.io.PrintWriter
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "java.io.PrintWriter")));
        // import java.util.logging.Logger
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "java.util.logging.Logger")));
        // import java.sql.SQLFeatureNotSupportedException
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "java.sql.SQLFeatureNotSupportedException")));
        // com.easy.query.api.proxy.client.DefaultEasyEntityQuery
        psiJavaFileCopied.getImportList().add(elementFactory.createImportStatement(PsiJavaFileUtil.getPsiClass(project, "com.easy.query.api.proxy.client.DefaultEasyEntityQuery")));
    }

    private void addDataSourceInterfaceImplement(PsiClass psiClassCopied, PsiElementFactory elementFactory) {
        PsiMethod getConnectionMethod = elementFactory.createMethodFromText("public Connection getConnection() throws SQLException {\n" +
                "    return null;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(getConnectionMethod);

        PsiMethod getConnectionMethod2 = elementFactory.createMethodFromText("public Connection getConnection(String username, String password) throws SQLException {\n" +
                "    return null;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(getConnectionMethod2);


        PsiMethod getLogWriterMethod = elementFactory.createMethodFromText("public PrintWriter getLogWriter() throws SQLException {\n" +
                "    return null;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(getLogWriterMethod);

        PsiMethod setLogWriterMethod = elementFactory.createMethodFromText("public void setLogWriter(PrintWriter out) throws SQLException {\n" +
                "\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(setLogWriterMethod);

        PsiMethod setLoginTimeoutMethod = elementFactory.createMethodFromText("public void setLoginTimeout(int seconds) throws SQLException {\n" +
                "\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(setLoginTimeoutMethod);

        PsiMethod getLoginTimeoutMethod = elementFactory.createMethodFromText("public int getLoginTimeout() throws SQLException {\n" +
                "    return 0;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(getLoginTimeoutMethod);

        PsiMethod getParentLoggerMethod = elementFactory.createMethodFromText("public Logger getParentLogger() throws SQLFeatureNotSupportedException {\n" +
                "    return null;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(getParentLoggerMethod);

        PsiMethod unwrapMethod = elementFactory.createMethodFromText("public <T> T unwrap(Class<T> iface) throws SQLException {\n" +
                "    return null;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(unwrapMethod);

        PsiMethod isWrapperForMethod = elementFactory.createMethodFromText("public boolean isWrapperFor(Class<?> iface) throws SQLException {\n" +
                "    return false;\n" +
                "  }", psiClassCopied);
        psiClassCopied.add(isWrapperForMethod);

    }


}
