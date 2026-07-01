package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;

@Internal
// TODO location
// TODO name
public class ToolExecutionRequestBuilder {

    private final AtomicReference<Integer> index;

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> name = new AtomicReference<>();
    private final StringBuffer arguments = new StringBuffer();

    /**
     * 获取当前已累积的参数字符串（不重置状态）。
     * 用于流式调用时获取完整的增量参数，而非单个 delta 片段。
     */
    public String accumulatedArguments() {
        return arguments.toString();
    }

    private final List<ToolExecutionRequest> allToolExecutionRequests = new ArrayList<>();

    public ToolExecutionRequestBuilder() {
        this(0);
    }

    public ToolExecutionRequestBuilder(int index) {
        this.index = new AtomicReference(index);
    }

    public int index() {
        return index.get();
    }

    public int updateIndex(Integer index) {
        if (index != null) {
            this.index.set(index);
        }
        return this.index.get();
    }

    public String id() {
        return id.get();
    }

    public String updateId(String id) {
        if (isNotNullOrBlank(id)) {
            this.id.set(id);
        }
        return this.id.get();
    }

    public String name() {
        return name.get();
    }

    public String updateName(String name) {
        if (isNotNullOrBlank(name)) {
            this.name.set(name);
        }
        return this.name.get();
    }

    public void appendArguments(String partialArguments) {
        if (isNotNullOrEmpty(partialArguments)) {
            arguments.append(partialArguments);
        }
    }

    public ToolExecutionRequest build() {
        // TODO store it till complete response?
        String arguments = this.arguments.toString();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id(id.get())
                .name(name.get())
                .arguments(arguments.isEmpty() ? "{}" : arguments)
                .build();
        allToolExecutionRequests.add(toolExecutionRequest); // TODO method name, rethink
        reset();
        return toolExecutionRequest;
    }

    private void reset() {
        id.set(null);
        name.set(null);
        arguments.setLength(0);
    }

    public boolean hasToolExecutionRequests() {
        return !allToolExecutionRequests.isEmpty() || name.get() != null;
    }

    public List<ToolExecutionRequest> allToolExecutionRequests() {
        return allToolExecutionRequests;
    }
}