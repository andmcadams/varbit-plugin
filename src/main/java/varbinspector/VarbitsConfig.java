package varbinspector;

import java.util.UUID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("varbits")
public interface VarbitsConfig extends Config
{
	static final String SESSION_VALUE = UUID.randomUUID().toString();
	@ConfigItem(
		keyName = "sessionValue",
		name = "Session ID",
		description = "Session ID to use when manual toggle is on. Set this first."
	)
	default String sessionValue()
	{
		return SESSION_VALUE;
	}

	@ConfigItem(
		keyName = "sessionToggle",
		name = "Manual toggle",
		description = "Use a manually input session ID. Set the session ID before turning on."
	)
	default boolean sessionToggle()
	{
		return false;
	}

}
