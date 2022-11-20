package io.github.rjaros87.storage;

public enum EventFields {
    LIKE("like"),
    DISLIKE("dislike"),
    CONTENT("content"),
    CATEGORY("category"),
    USERNAME("username")
    ;

    private final String fieldKey;

    EventFields(String key) {
        fieldKey = key;
    }

    public static EventFields findByKey(String key){
        for(EventFields v : values()){
            if( v.getFieldKey().equals(key)){
                return v;
            }
        }
        return null;
    }

    public String getFieldKey() {
        return fieldKey;
    }
}
