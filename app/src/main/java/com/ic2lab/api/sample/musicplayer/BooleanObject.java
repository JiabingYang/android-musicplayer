package com.ic2lab.api.sample.musicplayer;

public class BooleanObject<T> {
    private T object;
    private boolean bool;

    public BooleanObject(T object, boolean bool) {
        this.object = object;
        this.bool = bool;
    }

    public T getObject() {
        return object;
    }

    public void setObject(T object) {
        this.object = object;
    }

    public boolean isBool() {
        return bool;
    }

    public void setBool(boolean bool) {
        this.bool = bool;
    }
}
