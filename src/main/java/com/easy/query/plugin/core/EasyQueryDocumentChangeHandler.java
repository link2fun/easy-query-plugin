package com.easy.query.plugin.core;

import com.easy.query.plugin.core.config.CustomConfig;
import com.easy.query.plugin.core.entity.AptFileCompiler;
import com.easy.query.plugin.core.entity.AptPropertyInfo;
import com.easy.query.plugin.core.entity.AptSelectPropertyInfo;
import com.easy.query.plugin.core.entity.AptSelectorInfo;
import com.easy.query.plugin.core.entity.AptValueObjectInfo;
import com.easy.query.plugin.core.enums.FileTypeEnum;
import com.easy.query.plugin.core.util.BooleanUtil;
import com.easy.query.plugin.core.util.ClassUtil;
import com.easy.query.plugin.core.util.KtFileUtil;
import com.easy.query.plugin.core.util.Modules;
import com.easy.query.plugin.core.util.ObjectUtil;
import com.easy.query.plugin.core.util.ProjectUtils;
import com.easy.query.plugin.core.util.PsiJavaFileUtil;
import com.easy.query.plugin.core.util.PsiUtil;
import com.easy.query.plugin.core.util.StrUtil;
import com.easy.query.plugin.core.util.VelocityUtils;
import com.easy.query.plugin.core.util.VirtualFileUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.messages.MessageBus;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.velocity.VelocityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * @author bigtian
 */
public class EasyQueryDocumentChangeHandler implements DocumentListener, EditorFactoryListener, Disposable, FileEditorManagerListener {
    private static final Logger log = Logger.getInstance(EasyQueryDocumentChangeHandler.class);
    public static final Key<Boolean> CHANGE = Key.create("change" );
    private static final Key<Boolean> LISTENER = Key.create("listener" );
//    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    }

    public static void createAptFile(List<VirtualFile> virtualFiles, Project project) {
//        Project project = ProjectUtils.getCurrentProject();
        virtualFiles = virtualFiles.stream()
                .filter(oldFile -> {
                    if (Objects.isNull(oldFile)) {
                        return false;
                    }
                    Boolean userData = oldFile.getUserData(CHANGE);
                    return !(Objects.isNull(oldFile) || (!oldFile.getName().endsWith(".java" ) && !oldFile.getName().endsWith(".kt" )) || !oldFile.isWritable()) && BooleanUtil.isTrue(userData) && checkFile(project, oldFile);
                }).collect(Collectors.toList());
        Map<PsiDirectory, List<PsiFile>> psiDirectoryMap = new HashMap<>();
        try {

            // 检查索引是否已准备好
            for (VirtualFile oldFile : virtualFiles) {
                Module moduleForFile = ModuleUtil.findModuleForFile(oldFile, project);
                if (moduleForFile == null) {
                    log.warn("moduleForFile is null," + oldFile.getName());
                    continue;
                }
                CustomConfig config = Modules.moduleConfig(moduleForFile);
                if (!ObjectUtil.defaultIfNull(config.isEnable(), true)) {
                    continue;
                }
                String moduleDirPath = Modules.getPath(moduleForFile);
                PsiClassOwner psiFile = (PsiClassOwner) VirtualFileUtils.getPsiFile(project, oldFile);
                FileTypeEnum fileType = PsiUtil.getFileType(psiFile);
                String path = moduleDirPath + CustomConfig.getConfig(config.getGenPath(),
                        fileType, Modules.isManvenProject(moduleForFile))
                        + psiFile.getPackageName().replace(".", "/" ) + "/proxy";

                PsiDirectory psiDirectory = VirtualFileUtils.createSubDirectory(moduleForFile, path);
                // 等待索引准备好
                DumbService.getInstance(project).runWhenSmart(() -> {
                    // 在智能模式下，执行需要等待索引准备好的操作，比如创建文件
                    // 创建文件等操作代码
                    oldFile.putUserData(CHANGE, false);
                    PsiClass[] classes = psiFile.getClasses();
                    if (classes.length == 0) {
                        log.warn("psiJavaFile.getText():[" + psiFile.getText() + "],psiJavaFile.getClasses() is empty" );
                        return;
                    }
                    PsiClass psiClass = psiFile.getClasses()[0];
                    PsiAnnotation entityProxy = psiClass.getAnnotation("com.easy.query.core.annotation.EntityProxy" );
                    if (entityProxy == null) {
                        log.warn("annotation [EntityProxy] is null" );
                        return;
                    }
//                    PsiClass anInterface = JavaPsiFacade.getInstance(project).getElementFactory().createInterface("ProxyEntityAvailable<Topic, TopicProxy>" );
//                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
//                    PsiClass anInterface1 = elementFactory.createInterface("com.easy.query.core.proxy.ProxyEntityAvailable" );
//                    psiClass.add(anInterface1);

//                    PsiClass[] interfaces = psiClass.getInterfaces();
                    // 创建接口添加到PsiClass中
                    if (psiClass.getInterfaces().length == 0) {

                    }
//                    PsiClass psiClass1 = JavaPsiFacade.getInstance(project).findClass("org.example.entity.ProxyEntityAvailable", GlobalSearchScope.allScope(project));
//                    if(psiClass1!=null){
//                        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
//                        PsiJavaCodeReferenceElement referenceElementByType = elementFactory.createClassReferenceElement(psiClass);
////                        PsiJavaCodeReferenceElement ref = elementFactory.createClassReferenceElement(psiClass1);
////                        String text = psiClass1.getText();
////                        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
////                        PsiClassType typeWithSubstitutor = elementFactory.createType(psiClass, substitutor);
////                        PsiJavaCodeReferenceElement parameterRef = elementFactory.createReferenceElementByType(typeWithSubstitutor);
//                        PsiJavaCodeReferenceElement referenceFromText = elementFactory.createReferenceFromText("ProxyEntityAvailable<TopicProxy>", psiClass);
//
//                        PsiMethod method = elementFactory.createMethodFromText("public Class<TopicProxy> proxyTableClass() {return TopicProxy.class;}",psiClass);
//                        method.getModifierList().addAnnotation("Override");
////                        PsiCodeBlock codeBlockFromText = elementFactory.createCodeBlockFromText("implements ProxyEntityAvailable<Topic, TopicProxy>", psiClass);
//                        WriteCommandAction.runWriteCommandAction(project,()->{
//
////                            ref.getParameterList().add(parameterRef);
//                            psiClass.getImplementsList().add(referenceFromText);
//                            psiClass.add(method);
//                        });
//                    }

                    String entityName = psiClass.getName();
                    String entityFullName = psiClass.getQualifiedName();
                    //获取对应的代理对象名称
                    String proxyEntityName = PsiUtil.getPsiAnnotationValueIfEmpty(entityProxy, "value", psiClass.getName() + "Proxy" );
                    //代理对象属性忽略
                    Set<String> proxyIgnoreProperties = PsiUtil.getPsiAnnotationValues(entityProxy, "ignoreProperties", new HashSet<>());
                    //是否是数据库对象
                    PsiAnnotation entityTable = psiClass.getAnnotation("com.easy.query.core.annotation.Table" );
                    //获取对应的忽略属性
                    Set<String> tableAndProxyIgnoreProperties = PsiUtil.getPsiAnnotationValues(entityTable, "ignoreProperties", proxyIgnoreProperties);

                    PsiField[] fields = psiClass.getAllFields();

                    AptValueObjectInfo aptValueObjectInfo = new AptValueObjectInfo(entityName);
                    String packageName = psiFile.getPackageName() + "." + ObjectUtil.defaultIfEmpty(config.getAllInTablesPackage(), "proxy" );
                    AptFileCompiler aptFileCompiler = new AptFileCompiler(packageName, entityName, proxyEntityName,new AptSelectorInfo(proxyEntityName+"Selector"));
                    aptFileCompiler.addImports(entityFullName);
                    for (PsiField field : fields) {
                        PsiAnnotation columnIgnore = field.getAnnotation("com.easy.query.core.annotation.ColumnIgnore" );
                        if (columnIgnore != null) {
                            continue;
                        }
                        String name = field.getName();
                        //是否存在忽略属性
                        if (!tableAndProxyIgnoreProperties.isEmpty() && tableAndProxyIgnoreProperties.contains(name)) {
                            continue;
                        }
                        boolean isBeanProperty = ClassUtil.hasGetterAndSetter(psiClass, name);
                        if (!isBeanProperty) {
                            continue;
                        }
                        String psiFieldPropertyType = PsiUtil.getPsiFieldPropertyType(field);
                        String psiFieldComment = PsiUtil.getPsiFieldClearComment(field);
                        PsiAnnotation valueObject = field.getAnnotation("com.easy.query.core.annotation.ValueObject" );
                        boolean isValueObject = valueObject != null;
                        String fieldName = isValueObject ? psiFieldPropertyType.substring(psiFieldPropertyType.lastIndexOf("." ) + 1) : entityName;
                        aptValueObjectInfo.getProperties().add(new AptPropertyInfo(name, psiFieldPropertyType, psiFieldComment, fieldName, isValueObject, entityName));
                        aptFileCompiler.getSelectorInfo().getProperties().add(new AptSelectPropertyInfo(name,psiFieldComment));
                        if (isValueObject) {
                            aptFileCompiler.addImports("com.easy.query.core.proxy.AbstractValueObjectProxyEntity" );
                            aptFileCompiler.addImports(psiFieldPropertyType);
                            PsiType fieldType = field.getType();
                            PsiClass fieldClass = ((PsiClassType) fieldType).resolve();
                            if (fieldClass == null) {
                                log.warn("field [" + name + "] is value object,cant resolve PsiClass" );
                                continue;
                            }
                            AptValueObjectInfo fieldAptValueObjectInfo = new AptValueObjectInfo(fieldClass.getName());
                            aptValueObjectInfo.getChildren().add(fieldAptValueObjectInfo);
                            addValueObjectClass(name, fieldAptValueObjectInfo, fieldClass, aptFileCompiler, tableAndProxyIgnoreProperties);
                        }

                    }

                    VelocityContext context = new VelocityContext();
                    context.put("aptValueObjectInfo", aptValueObjectInfo);
                    context.put("aptFileCompiler", aptFileCompiler);
                    String suffix = Modules.getProjectTypeSuffix(moduleForFile);
                    PsiFile psiProxyFile = VelocityUtils.render(context, Template.getTemplateContent("AptTemplate" + suffix), proxyEntityName + suffix);
                    psiDirectoryMap.computeIfAbsent(psiDirectory, k -> new ArrayList<>()).add(psiProxyFile);
                });
            }
            // 等待索引准备好
            DumbService.getInstance(project).runWhenSmart(() -> {
                // 执行需要索引的操作
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    for (Map.Entry<PsiDirectory, List<PsiFile>> entry : psiDirectoryMap.entrySet()) {
                        PsiDirectory psiDirectory = entry.getKey();
                        List<PsiFile> psiFiles = entry.getValue();
                        for (PsiFile tmpFile : psiFiles) {
                            PsiFile file = psiDirectory.findFile(tmpFile.getName());
                            if (ObjectUtil.isNotNull(file)) {
                                String text = tmpFile.getText();
                                Document document = file.getViewProvider().getDocument();
                                if(!Objects.equals(document.getText(),text)){
                                    document.setText(text);
                                }
                            } else {
                                psiDirectory.add(tmpFile);
                            }
                        }
                    }
                });
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addValueObjectClass(String parentProperty, AptValueObjectInfo aptValueObjectInfo, PsiClass fieldValueObjectClass, AptFileCompiler aptFileCompiler, Set<String> tableAndProxyIgnoreProperties) {
        PsiField[] allFields = fieldValueObjectClass.getAllFields();

        String entityName = fieldValueObjectClass.getName();
        aptFileCompiler.addImports(fieldValueObjectClass.getQualifiedName());
        for (PsiField field : allFields) {
            PsiAnnotation columnIgnore = field.getAnnotation("com.easy.query.core.annotation.ColumnIgnore" );
            if (columnIgnore != null) {
                continue;
            }
            String name = field.getName();
            //是否存在忽略属性
            if (!tableAndProxyIgnoreProperties.isEmpty() && tableAndProxyIgnoreProperties.contains(parentProperty + "." + name)) {
                continue;
            }
            boolean isBeanProperty = ClassUtil.hasGetterAndSetter(fieldValueObjectClass, name);
            if (!isBeanProperty) {
                continue;
            }
            String psiFieldPropertyType = PsiUtil.getPsiFieldPropertyType(field);
            String psiFieldComment = PsiUtil.getPsiFieldClearComment(field);
            PsiAnnotation valueObject = field.getAnnotation("com.easy.query.core.annotation.ValueObject" );
            boolean isValueObject = valueObject != null;
            String fieldName = isValueObject ? psiFieldPropertyType.substring(psiFieldPropertyType.lastIndexOf("." ) + 1) : entityName;
            aptValueObjectInfo.getProperties().add(new AptPropertyInfo(name, psiFieldPropertyType, psiFieldComment, fieldName, isValueObject, entityName));

            if (valueObject != null) {
                aptFileCompiler.addImports(psiFieldPropertyType);
                PsiType fieldType = field.getType();
                PsiClass fieldClass = ((PsiClassType) fieldType).resolve();
                if (fieldClass == null) {
                    log.warn("field [" + name + "] is value object,cant resolve PsiClass" );
                    continue;
                }
                AptValueObjectInfo innerValueObject = new AptValueObjectInfo(fieldClass.getName());
                aptValueObjectInfo.getChildren().add(innerValueObject);
                addValueObjectClass(parentProperty + "." + name, innerValueObject, fieldClass, aptFileCompiler, tableAndProxyIgnoreProperties);
            }
        }
    }


    public EasyQueryDocumentChangeHandler() {
        super();

        try {
            // 所有的文档监听
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
            //获取已打开的编辑器
            Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
            for (Editor editor : allEditors) {
                ProjectUtils.setCurrentProject(editor.getProject());
                addEditorListener(editor);
            }
            Project project = ProjectUtils.getCurrentProject();
            if (Objects.isNull(project)) {
                return;
            }
//            Deprecated
//            Use com.intellij.util.messages.MessageBus instead: see FileEditorManagerListener.FILE_EDITOR_MANAGER

//            FileEditorManager.getInstance(project).addFileEditorManagerListener(this);
            MessageBus messageBus = project.getMessageBus();
            messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        } catch (Exception e) {
            log.error("初始化EasyQueryDocumentChangeHandler出错:" + e.getMessage(), e);
        }

    }


    private void addEditorListener(Editor editor) {
        Document document = editor.getDocument();
        if (BooleanUtils.isNotTrue(document.getUserData(LISTENER))) {
            editor.addEditorMouseListener(new EditorMouseListener() {
                @Override
                public void mouseExited(@NotNull EditorMouseEvent event) {
                    createAptFile(Collections.singletonList(VirtualFileUtils.getVirtualFile(editor.getDocument())), event.getEditor().getProject());
                }
            });
            document.putUserData(LISTENER, true);
            document.addDocumentListener(this);
        }
    }

    private void removeEditorListener(Editor editor) {
        Document document = editor.getDocument();
        if (BooleanUtils.isTrue(document.getUserData(LISTENER))) {
            document.putUserData(LISTENER, false);
            document.removeDocumentListener(this);
        }
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {

        EditorFactoryListener.super.editorCreated(event);
        Editor editor = event.getEditor();
        addEditorListener(editor);
        ProjectUtils.setCurrentProject(editor.getProject());
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        removeEditorListener(editor);
    }


    private static boolean checkFile(Project project, VirtualFile currentFile) {
        if (Objects.isNull(currentFile) || currentFile instanceof LightVirtualFile) {
            return false;
        }
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.findFile(currentFile);
        // 支持java和kotlin
        if (!(psiFile instanceof PsiJavaFile) && !(psiFile instanceof KtFile)) {
            return false;
        }
        Set<String> importSet = new HashSet<>();
        if (psiFile instanceof KtFile) {
            KtFile ktFile = (KtFile) psiFile;
            importSet = KtFileUtil.getImportSet(ktFile);
        }
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            importSet = PsiJavaFileUtil.getQualifiedNameImportSet(psiJavaFile);
        }
        return importSet.contains("com.easy.query.core.annotation.EntityProxy" );
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        CharSequence newFragment = event.getNewFragment();
        if ((StrUtil.isBlank(newFragment) && StrUtil.isBlank(event.getOldFragment()))) {
            return;
        }
        VirtualFile currentFile = VirtualFileUtils.getVirtualFile(document);
        if (Objects.nonNull(currentFile)) {
            currentFile.putUserData(CHANGE, true);
        }
    }


    @Override
    public void dispose() {
        Disposer.dispose(this);
    }
}
