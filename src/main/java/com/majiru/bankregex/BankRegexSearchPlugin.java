package com.majiru.bankregex;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@PluginDescriptor(
	name = "Regex Bank Search"
)
public class BankRegexSearchPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BankRegexSearchConfig config;

	@Inject
	private ItemManager itemManager;

	private boolean regexSearch(final int itemId, final String str)
	{
		if (!str.startsWith("/") || str.length() < 2)
		{
			return false;
		}
		final String query = str.endsWith("/") ? str.substring(1, str.length() - 1) : str.substring(1);
		final String rawName = itemManager.getItemComposition(itemId).getName();
		final String name = config.ignoreCase() ? rawName.toLowerCase() : rawName;
		try
		{
			final Pattern pattern = Pattern.compile(query);
			if (!pattern.matcher(name).find(0))
			{
				return false;
			}
			return true;
		}
		catch (PatternSyntaxException e)
		{
			log.debug("User supplied query exception:", e);
		}
		return false;
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!event.getEventName().equals("bankSearchFilter"))
		{
			return;
		}

		int[] intStack = client.getIntStack();
		String[] stringStack = client.getStringStack();
		int intStackSize = client.getIntStackSize();
		int stringStackSize = client.getStringStackSize();

		int itemId = intStack[intStackSize - 1];
		String search = stringStack[stringStackSize - 1];
		if (regexSearch(itemId, search))
		{
			intStack[intStackSize - 2] = 1; //return true
		}
	}

	@Provides
	BankRegexSearchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankRegexSearchConfig.class);
	}
}
