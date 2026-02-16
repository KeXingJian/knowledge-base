package com.kxj.knowledgebase.constants;

public class CacheConstants {

    public static final String ANSWER_CACHE_PREFIX = "qa:answer:";
    public static final long ANSWER_CACHE_TTL = 3600;

    public static final String CONVERSATION_CACHE_PREFIX = "conversation:";
    public static final long CONVERSATION_CACHE_TTL = 7200;

    public static final String DOCUMENT_UPLOAD_PROGRESS_PREFIX = "document:upload:progress:";
    public static final long DOCUMENT_UPLOAD_PROGRESS_TTL = 3600;

    public static final String BATCH_UPLOAD_TASK_PREFIX = "document:batch:task:";
    public static final long BATCH_UPLOAD_TASK_TTL = 7200;

}