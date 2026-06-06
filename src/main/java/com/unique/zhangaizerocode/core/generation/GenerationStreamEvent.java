package com.unique.zhangaizerocode.core.generation;

public record GenerationStreamEvent(String event, String data) {

    public static GenerationStreamEvent message(String data) {
        return new GenerationStreamEvent(null, data);
    }

    public static GenerationStreamEvent snapshot(String data) {
        return new GenerationStreamEvent("snapshot", data);
    }

    public static GenerationStreamEvent done() {
        return new GenerationStreamEvent("done", "{}");
    }

    public static GenerationStreamEvent error(String message) {
        return new GenerationStreamEvent("error", message);
    }
}
