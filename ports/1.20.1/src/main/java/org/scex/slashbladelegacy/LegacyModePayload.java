package org.scex.slashbladelegacy;

import java.util.UUID;

public final class LegacyModePayload {
    private LegacyModePayload() {}
    public record Request() {}
    public record State(UUID player, boolean modern) {}
}
