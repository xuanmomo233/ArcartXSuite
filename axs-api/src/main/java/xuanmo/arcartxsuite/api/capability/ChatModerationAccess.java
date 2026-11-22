package xuanmo.arcartxsuite.api.capability;

import org.bukkit.command.CommandSender;

/** Operator-facing access to Chat reports and committed moderation receipts. */
@PublicCapability
public interface ChatModerationAccess {
    void inspect(CommandSender operator, String action, String argument);
}
