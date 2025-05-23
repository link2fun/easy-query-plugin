package com.easy.query.plugin.core.contributor;

import com.easy.query.plugin.core.contributor.java.EasyAnonymousContributor;
import com.easy.query.plugin.core.contributor.java.EasyContributor;
import com.easy.query.plugin.core.contributor.java.EasyOrderContributor;
import com.easy.query.plugin.core.contributor.java.EasySelectContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtEntitySetColumnsContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtGroupContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtGroupTableContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtIncludeContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtIncludesContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtOrderContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtSelectContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtSelectDraftContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtSelectorContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtSetIgnoreColumnsContributor;
import com.easy.query.plugin.core.contributor.kt.EasyKtWhereColumnsContributor;
import com.easy.query.plugin.core.util.TrieTree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * create time 2024/9/20 08:54
 * 文件说明
 *
 * @author xuejiaming
 */
public class BaseKotlinEasyQueryApiCompletionContributor {


    protected static final Set<EasyKtContributor> API_METHODS = new HashSet<>(Arrays.asList(
            new EasyKtSelectContributor("select", "select"),
            new EasyKtSelectContributor("having", "having"),
            new EasyKtSelectContributor("include", "include"),
            new EasyKtSelectContributor("includes", "includes"),
//            new EasySelectEntityVOContributor("select", "selectv", false),
            new EasyKtContributor("where", "where"),
            new EasyKtContributor("where_block", "where"),
//            new EasyKtContributor("where", "where_code_block", true)
            new EasyKtIncludeContributor("include", "include"),
            new EasyKtIncludesContributor("includes", "includes"),
//            new EasyIncludesContributor("includes", "includes", false),
            new EasyKtOrderContributor("orderBy", "orderBy"),
//            new EasyOrderContributor("orderBy", "orderBy_code_block", true),
            new EasyKtGroupContributor("groupBy", "groupBy"),
            new EasyKtGroupTableContributor("groupBy", "groupByTable"),
            new EasyKtEntitySetColumnsContributor("setColumns", "setColumns"),
            new EasyKtSelectorContributor("select", "selector"),
            new EasyKtWhereColumnsContributor("whereColumns", "whereColumns"),
            new EasyKtSetIgnoreColumnsContributor("setIgnoreColumns", "setIgnoreColumns"),
            new EasyKtSelectDraftContributor("select", "selectDraft")
//            new EasySelectContributor("having", "having", false),
//            new EasySelectContributor("having", "having_code_block", true),
//            new EasySelectContributor("selectColumn", "selectColumn", false),
//            new EasyFetchByContributor("fetchBy", "fetchBy", false),//支持弹窗选择
//            new EasyExpressionSetColumnsContributor("setColumns", "setColumns", false),
//            new EasyExpressionSetColumnsContributor("setColumns", "setColumns_code_block", true),
//            new EasyKtEntitySetColumnsContributor("setColumns", "setColumns", false),
//            new EasySetIgnoreColumnsContributor("setIgnoreColumns", "setIgnoreColumns", false),
//            new EasySetIgnoreColumnsContributor("setIgnoreColumns", "setIgnoreColumns_code_block", true),
//            new EasyWhereColumnsContributor("whereColumns", "whereColumns", false),
//            new EasyWhereColumnsContributor("whereColumns", "whereColumns_code_block", true)
    ));
    protected static final TrieTree API_MATCH_TREE = new TrieTree(API_METHODS.stream().map(o -> o.getTipWord()).collect(Collectors.toList()));
    protected static final Set<EasyContributor> ON_METHODS = new HashSet<>(Arrays.asList(
            new EasyOnContributor("", "on", false),
            new EasyOnContributor("", "on_code_block", true)));
    protected static final TrieTree ON_MATCH_TREE = new TrieTree(ON_METHODS.stream().map(o -> o.getTipWord()).collect(Collectors.toList()));
    protected static final Set<String> JOIN_METHODS = new HashSet<>(Arrays.asList("leftJoin", "rightJoin", "innerJoin"));
    protected static final Set<String> PREDICATE_METHODS = new HashSet<>(Arrays.asList("leftJoin", "rightJoin", "innerJoin", "where", "having"));


    protected static final Set<EasyContributor> ANONYMOUS_METHODS = new HashSet<>(Arrays.asList(
            new EasyAnonymousContributor("", "anonymous", false)));
    protected static final TrieTree ANONYMOUS_MATCH_TREE = new TrieTree(ANONYMOUS_METHODS.stream().map(o -> o.getTipWord()).collect(Collectors.toList()));

    protected static final Set<EasyContributor> COMPARE_GREATER_METHODS = new HashSet<>(Arrays.asList(
            new EasyCompareContributor("", ">", ".gt()"),
            new EasyCompareContributor("", ">=", ".ge()")));
    protected static final Set<EasyContributor> COMPARE_EQUALS_METHODS = new HashSet<>(Arrays.asList(
            new EasyCompareContributor("", "==", ".eq()")));
    protected static final Set<EasyContributor> COMPARE_NOT_EQUALS_METHODS = new HashSet<>(Arrays.asList(
            new EasyCompareContributor("", "!=", ".ne()")));
    protected static final Set<EasyContributor> SET_VALUE_METHODS = new HashSet<>(Arrays.asList(
            new EasyCompareContributor("", "=", ".set()")));
    protected static final Set<EasyContributor> COMPARE_LESS_METHODS = new HashSet<>(Arrays.asList(
            new EasyCompareContributor("", "<", ".lt()"),
            new EasyCompareContributor("", "<=", ".le()")));
}
