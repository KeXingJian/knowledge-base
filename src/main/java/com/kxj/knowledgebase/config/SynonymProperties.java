package com.kxj.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 同义词配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "cache.synonym")
public class SynonymProperties {

    /**
     * 是否启用同义词替换
     */
    private boolean enabled = true;

    /**
     * 同义词映射表：key=口语化表达，value=标准表达
     */
    private Map<String, String> mappings = new HashMap<>();

    /**
     * 默认同义词（当配置文件未指定时使用）
     */
    public Map<String, String> getDefaultMappings() {
        return Map.ofEntries(
                // ===== 疑问词 =====
                Map.entry("咋", "怎么"),
                Map.entry("啥", "什么"),
                Map.entry("为啥", "为什么"),
                Map.entry("咋样", "怎么样"),
                Map.entry("何", "什么"),
                Map.entry("何以", "为什么"),

                // ===== 动词短语 =====
                Map.entry("咋用", "怎么用"),
                Map.entry("咋配置", "怎么配置"),
                Map.entry("咋连", "怎么连接"),
                Map.entry("咋实现", "怎么实现"),
                Map.entry("咋弄", "怎么弄"),
                Map.entry("咋搞", "怎么做"),
                Map.entry("咋写", "怎么写"),
                Map.entry("咋启动", "怎么启动"),
                Map.entry("咋部署", "怎么部署"),
                Map.entry("咋安装", "怎么安装"),

                // ===== 查询类动词 =====
                Map.entry("介绍一下", "什么是"),
                Map.entry("讲讲", "什么是"),
                Map.entry("说说", "什么是"),
                Map.entry("解释下", "什么是"),
                Map.entry("描述一下", "什么是"),
                Map.entry("谈谈", "什么是"),
                Map.entry("概述", "什么是"),
                Map.entry("总结一下", "什么是"),

                // ===== 倒装归一化 =====
                Map.entry("是什么", "什么是"),
                Map.entry("是啥", "什么是"),
                Map.entry("是个啥", "什么是"),

                // ===== 常用技术缩写 =====
                Map.entry("sb", "springboot"),
                Map.entry("spring boot", "springboot"),
                Map.entry("k8s", "kubernetes"),
                Map.entry("k8s", "kubernetes"),
                Map.entry("db", "数据库"),
                Map.entry("api", "接口"),
                Map.entry("ui", "界面"),
                Map.entry("config", "配置"),
                Map.entry("cfg", "配置"),
                Map.entry("env", "环境"),
                Map.entry("dev", "开发"),
                Map.entry("prod", "生产"),
                Map.entry("kbservice", "kbservice"),

                // ===== 口语化连接词 =====
                Map.entry("给我", ""),
                Map.entry("帮我", ""),
                Map.entry("请", ""),
                Map.entry("告诉我", ""),
                Map.entry("我想知道", ""),
                Map.entry("我想了解", ""),

                // ===== 技术动词 =====
                Map.entry("配一下", "配置"),
                Map.entry("配个", "配置"),
                Map.entry("连一下", "连接"),
                Map.entry("写个", "写"),
                Map.entry("做个", "做"),
                Map.entry("整一个", "创建"),
                Map.entry("搞一个", "创建"),
                Map.entry("弄一个", "创建")
        );
    }

    /**
     * 获取有效的同义词映射（配置文件 + 默认值合并）
     */
    public Map<String, String> getEffectiveMappings() {
        Map<String, String> effective = new HashMap<>(getDefaultMappings());
        // 配置文件的值覆盖默认值
        effective.putAll(mappings);
        return effective;
    }
}