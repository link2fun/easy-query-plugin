package com.easy.query.plugin.core.entity;

/**
 * create time 2024/2/23 11:12
 * 文件说明
 *
 * @author xuejiaming
 */
public class ValueHolder<T> {
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
