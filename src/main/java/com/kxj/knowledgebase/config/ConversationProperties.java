package com.kxj.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "conversation")
public class ConversationProperties {

    private int maxHistoryMessages = 10;

    private int maxConversationDays = 30;

    private boolean enableAutoTitle = true;

    private int titleMaxLength = 50;
}
