package com.jaguarliu.ai.agents;

import java.util.regex.Pattern;

public final class AgentConstants {
    private AgentConstants() {}

    public static final String DEFAULT_AGENT_ID = "main";
    public static final String DEFAULT_AGENT_NAME = "main";
    public static final String DEFAULT_AGENT_DISPLAY_NAME = "Main Agent";

    public static final int MAX_AGENT_ID_LENGTH = 64;
    public static final Pattern SAFE_AGENT_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]{1," + MAX_AGENT_ID_LENGTH + "}$");
}
