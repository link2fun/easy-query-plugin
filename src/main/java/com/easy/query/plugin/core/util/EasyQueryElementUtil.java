package com.easy.query.plugin.core.util;


import cn.hutool.core.util.ObjUtil;
import com.easy.query.plugin.core.config.ProjectSettings;
import com.easy.query.plugin.core.entity.InspectionResult;
import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import groovy.lang.Tuple3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EasyQuery 相关元素工具类
 *
 * @author link2fun
 */
public class EasyQueryElementUtil {


    /**
     * 检查DTO上的 @Column 注解是否需要更新
     *
     * @param projectSettings 项目级别的设置
     * @param dtoField        DTO上的字段
     * @param entityField     实体上的字段
     * @return InspectionResult 检查结果
     */
    public static InspectionResult inspectionColumnAnnotation(ProjectSettings projectSettings, PsiField dtoField, PsiField entityField) {

        if (Objects.isNull(dtoField) || Objects.isNull(entityField)) {
            // 任意一个字段为空, 应该是没法检查的, 直接当做没有问题
            return InspectionResult.noProblem();
        }

        // 项目设置, 是否保留DTO上的@Column注解
        Boolean featureKeepDtoColumnAnnotation = Optional.ofNullable(projectSettings).map(ProjectSettings::getState).map(ProjectSettings.State::getFeatureKeepDtoColumnAnnotation).orElse(true);

        // 获取DTO上的 @Column 注解
        PsiAnnotation dtoAnnoColumn = dtoField.getAnnotation("com.easy.query.core.annotation.Column");
        PsiAnnotation entityAnnoColumn = entityField.getAnnotation("com.easy.query.core.annotation.Column");


        if (entityAnnoColumn == null && dtoAnnoColumn == null) {
            // 实体上和 DTO上都没有 @Column 注解, 应该也是无需判断的
            return InspectionResult.noProblem();
        }
        String entityColumnName = PsiUtil.getPsiAnnotationValue(entityAnnoColumn, "value", "");
        String dtoColumnName = PsiUtil.getPsiAnnotationValue(dtoAnnoColumn, "value", "");
        if (StrUtil.isBlank(entityColumnName) && StrUtil.isBlank(dtoColumnName)) {
            // 实体上和 DTO上的 @Column 注解 的 value属性, 应该也是无需判断的
            return InspectionResult.noProblem();
        }

        if (!featureKeepDtoColumnAnnotation) {
            if (Objects.isNull(dtoAnnoColumn)) {
                // 不需要保留, 且DTO上没有, 那么也是无需判断的
                return InspectionResult.noProblem();
            }
            // 设置了不保留DTO上的@Column注解, 但是DTO上有, 那么应该移除掉
            LocalQuickFix removeDtoAnnoColumn = new LocalQuickFix() {
                @Override
                public @IntentionFamilyName @NotNull String getFamilyName() {
                    //noinspection DialogTitleCapitalization
                    return "依照 EasyQuery 项目配置 不保留DTO上 @Column 注解";
                }

                @Override
                public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
                    problemDescriptor.getPsiElement().delete();
                }
            };

            return InspectionResult.newResult().addProblem(dtoAnnoColumn, "依照 EasyQuery 项目配置 不保留DTO上 @Column 注解", ProblemHighlightType.ERROR,
                Lists.newArrayList(removeDtoAnnoColumn)
            );

        }


        if (entityAnnoColumn == null) {
            // 实体上没有有, 但是DTO上有, 那么DTO上的应该移除掉


            LocalQuickFix removeDtoAnnoColumn = new LocalQuickFix() {
                @Override
                public @IntentionFamilyName @NotNull String getFamilyName() {
                    //noinspection DialogTitleCapitalization
                    return "移除DTO上的@Column注解";
                }

                @Override
                public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
                    problemDescriptor.getPsiElement().delete();
                }
            };

            return InspectionResult.newResult().addProblem(dtoAnnoColumn, "实体上没有@Column注解,DTO上的@Column应移除或保证Column.value的值两者一样", ProblemHighlightType.ERROR,
                Lists.newArrayList(removeDtoAnnoColumn)
            );
        }


        Map<String, PsiNameValuePair> entityAnnoColumnAttrMap = PsiJavaAnnotationUtil.attrToMap(entityAnnoColumn);
        Map<String, PsiNameValuePair> dtoAnnoColumnAttrMap = PsiJavaAnnotationUtil.attrToMap(dtoAnnoColumn);

        String[] ignoredKeys = {"primaryKey", "generatedKey", "primaryKeyGenerator"};
        Tuple3<Boolean, List<String>, Map<String, PsiNameValuePair>> annoColumnAttrCompareResult = PsiJavaAnnotationUtil.compareAttrMap(entityAnnoColumnAttrMap, dtoAnnoColumnAttrMap, ignoredKeys);

        List<String> errInfoList = annoColumnAttrCompareResult.getV2();
        Map<String, PsiNameValuePair> newAttrMap = annoColumnAttrCompareResult.getV3();
        if (annoColumnAttrCompareResult.getV1()) {
            // 属性一致, 不需要更新
            return InspectionResult.noProblem();
        }

        // 属性不一致

// 实体上有@Column 注解, 那么应该精简一下, 看看是否有必要更新
        PsiAnnotation dtoAnnoColumnNew = (PsiAnnotation) entityAnnoColumn.copy();
        PsiAnnotationParameterList parameterList = dtoAnnoColumnNew.getParameterList();
        PsiNameValuePair[] attributes = parameterList.getAttributes();
        int length = attributes.length;
        for (int i = length - 1; i >= 0; i--) {
            PsiNameValuePair attribute = attributes[i];
            if (!newAttrMap.containsKey(attribute.getAttributeName())) {
                attribute.delete();
            }
        }


        // 现在需要进行更新了

        SmartPsiElementPointer<PsiElement> newAnnoColumnPointer = SmartPointerManager.createPointer(dtoAnnoColumnNew);


        if (Objects.isNull(dtoAnnoColumn)) {
            // 这里分两种情况, 第一种, 是 DTO 上的注解不存在, 这时候是需要新增注解

            LocalQuickFix addColumnAnnoToDTO = new LocalQuickFix() {
                @Override
                public @IntentionFamilyName @NotNull String getFamilyName() {
                    //noinspection DialogTitleCapitalization
                    return "添加缺失的@Column注解";
                }

                @Override
                public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {


                    if (newAnnoColumnPointer.getElement() != null) {
                        // 缺少注解的时候异常信息绑定的是字段上
                        PsiField dtoFieldToFix = (PsiField) problemDescriptor.getPsiElement();
                        dtoFieldToFix.getModifierList().addBefore(newAnnoColumnPointer.getElement(), dtoFieldToFix.getModifierList().getFirstChild());

                    }


                }
            };
            return InspectionResult.newResult()
                .addProblem(dtoField, "需要在DTO上添加 @Column 注解", ProblemHighlightType.ERROR,
                    Lists.newArrayList(addColumnAnnoToDTO)
                );
        }

        // 现在是直接需要更新的


        // 另一种是, DTO上的注解存在, 这时候需要更新

        LocalQuickFix updateDtoAnnoColumn = new LocalQuickFix() {
            @Override
            public @IntentionFamilyName @NotNull String getFamilyName() {
                return "更新DTO上的 @Column 注解";
            }

            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
                if (newAnnoColumnPointer.getElement() != null) {

                    problemDescriptor.getPsiElement().replace(newAnnoColumnPointer.getElement());
                }
            }
        };


        return InspectionResult.newResult()
            .addProblem(dtoAnnoColumn, "@Column 注解需要更新: " + StrUtil.join("\n", errInfoList), ProblemHighlightType.ERROR,
                Lists.newArrayList(updateDtoAnnoColumn)
            );
    }


}
