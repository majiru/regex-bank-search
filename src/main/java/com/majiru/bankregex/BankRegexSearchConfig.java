package com.majiru.bankregex;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankregexsearch")
public interface BankRegexSearchConfig extends Config
{
	@ConfigItem(
		keyName = "ignoreCase",
		name = "Ignore item casing",
		description = "Ignore item name casing when searching"
	)
	default boolean ignoreCase()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requireTerminator",
		name = "Require Terminating /",
		description = "Forces queries to require a trailing slash"
	)
	default boolean requireTerminator() { return false; }
}
