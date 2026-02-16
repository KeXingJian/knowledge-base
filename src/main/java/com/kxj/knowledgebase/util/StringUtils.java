package com.kxj.knowledgebase.util;

public class StringUtils {

    public static String floatArrayToString(float[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 3;
    }

    private StringUtils() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }
}
