package com.easy.query.plugin.core.util;

import com.easy.query.plugin.core.enums.BeanPropTypeEnum;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Objects;

/**
 * create time 2023/9/16 12:46
 * 文件说明
 *
 * @author xuejiaming
 */
public class ClassUtil {


    public static BeanPropTypeEnum hasGetterAndSetter(PsiClass psiClass, String propertyName) {
        String capitalizedPropertyName = capitalize(propertyName);

        // 获取所有方法（包括继承的），避免触发PSI增强机制
        PsiMethod[] allMethods = psiClass.getMethods();

        // 检查是否有公共的 getter 方法
        BeanPropTypeEnum beanProp = findGetterMethod(allMethods, capitalizedPropertyName, "get");
        if(beanProp == BeanPropTypeEnum.NOT){
            beanProp = findGetterMethod(allMethods, capitalizedPropertyName, "is");
            if(beanProp == BeanPropTypeEnum.NOT){
                return beanProp;
            }
        }

        // 检查是否有公共的 setter 方法
        boolean hasPublicSetter = findSetterMethod(allMethods, capitalizedPropertyName);
        if(!hasPublicSetter){
            return BeanPropTypeEnum.NOT;
        }

        return beanProp;
    }

    private static BeanPropTypeEnum findGetterMethod(PsiMethod[] methods, String capitalizedPropertyName, String prefix) {
        String methodName = prefix + capitalizedPropertyName;
        for (PsiMethod method : methods) {
            if (methodName.equals(method.getName()) && method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return Objects.equals("is", prefix) ? BeanPropTypeEnum.IS : BeanPropTypeEnum.GET;
            }
        }
        return BeanPropTypeEnum.NOT;
    }

    private static boolean findSetterMethod(PsiMethod[] methods, String capitalizedPropertyName) {
        String methodName = "set" + capitalizedPropertyName;
        for (PsiMethod method : methods) {
            if (methodName.equals(method.getName()) && method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String s) {
        if (s.length() == 0) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    public static @Nullable PsiClass findClass(Project project, String fullClassName, boolean seachAllScope) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        PsiClass newClass = javaPsiFacade.findClass(fullClassName, GlobalSearchScope.projectScope(project));
        if(seachAllScope){
            if (newClass == null) {
                newClass = javaPsiFacade.findClass(fullClassName, GlobalSearchScope.allScope(project));
            }
        }
        return newClass;
    }
}
