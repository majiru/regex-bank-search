package com.majiru.bankregex;

import com.google.inject.Provides;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.SpriteID;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
	ChatboxPanelManager chatboxPanelManager;

	@Inject
	ClientThread clientThread;

	@Inject
	private BankRegexSearchConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configmanager;

	private static final String CONFIG_GROUP = "regexbanksearch";

	@AllArgsConstructor
	class Query
	{
		String name;
		String search;
		Pattern pattern;
	}

	private Query manual;
	private Query bookmark;
	private boolean filtering;

	@Override
	public void startUp()
	{
		manual = new Query(null, null, null);
	}

	@Provides
	BankRegexSearchConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankRegexSearchConfig.class);
	}

	private Optional<String> clean(final String query)
	{
		if (!query.startsWith("/") || query.length() < 2)
		{
			return Optional.empty();
		}
		if (config.requireTerminator() && !query.endsWith("/"))
		{
			return Optional.empty();
		}
		return query.endsWith("/") ? Optional.of(query.substring(1, query.length() - 1)) :
			Optional.of(query.substring(1));
	}

	private boolean search(Query q, final int itemId, final String str)
	{
		final Optional<String> userQuery = clean(str);
		if (!userQuery.isPresent())
		{
			return false;
		}

		final String rawName = itemManager.getItemComposition(itemId).getName();
		final String name = config.ignoreCase() ? rawName.toLowerCase() : rawName;
		if (q.search == null || !q.search.equals(userQuery.get()))
		{
			q.search = userQuery.get();
			try
			{
				q.pattern = Pattern.compile(q.search);
			}
			catch (PatternSyntaxException e)
			{
				q.pattern = null;
			}
		}
		if (q.pattern != null && q.pattern.matcher(name).find(0))
		{
			return true;
		}
		return false;
	}

	private boolean bookmarkSearch(final int itemId)
	{
		if (bookmark == null)
		{
			return false;
		}
		final String query = configmanager.getConfiguration(CONFIG_GROUP, "bookmark_" + bookmark.name);
		//Empty bookmark
		if (query == null)
		{
			return false;
		}
		return search(bookmark, itemId, "/" + query + "/");
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			bookmark = null;
			clientThread.invoke(() -> layoutBank(""));
		}
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
		if (bookmark != null)
		{
			if (bookmarkSearch(itemId))
			{
				intStack[intStackSize - 2] = 1; //return true
			}
			return;
		}
		if (search(manual, itemId, search))
		{
			intStack[intStackSize - 2] = 1; //return true
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (!filtering || bookmark == null || event.getScriptId() != ScriptID.BANKMAIN_SEARCHING)
		{
			return;
		}
		boolean bankOpen = client.getItemContainer(InventoryID.BANK) != null;
		if (client.getItemContainer(InventoryID.BANK) != null)
		{
			client.getIntStack()[client.getIntStackSize() - 1] = 1; // true
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		Widget bankSettings = client.getWidget(WidgetInfo.BANK_SETTINGS_BUTTON);
		if (bankSettings == null || bankSettings.isHidden())
		{
			return;
		}
		if (event.getOption().equals("Show menu"))
		{
			clientThread.invoke(this::buildMenus);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!filtering)
		{
			return;
		}
		Widget bankContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (bankContainer == null || bankContainer.isHidden())
		{
			return;
		}
		final String option = event.getMenuOption();
		if (option.startsWith("View tab") || option.equals("View all items")
			|| option.startsWith("View tag tab"))
		{
			bookmark = null;
			clientThread.invoke(() -> layoutBank(""));
		}
	}

	private void editBookmark(MenuEntry entry)
	{
		//Nothing to bookmark
		if (manual.search == null)
		{
			return;
		}
		chatboxPanelManager.openTextInput("Name of bookmark")
			.onDone((Consumer<String>) (newValue) ->
				clientThread.invoke(() ->
					{
						String name = newValue.replaceAll("[<>/]]", "");
						if (name.length() == 0)
						{
							return;
						}
						configmanager.setConfiguration(CONFIG_GROUP, "bookmark_" + name, manual.search);
						bookmark = new Query(name, null, null);
						manual.search = null;
					}

				))
			.build();
	}

	//must be called from clientThread
	private void layoutBank(final String query)
	{
		//A blank query indicates that we are resetting.
		filtering = !query.equals("");
		Widget bankContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (bankContainer == null || bankContainer.isHidden())
		{
			return;
		}

		Object[] scriptArgs = bankContainer.getOnInvTransmitListener();
		if (scriptArgs == null)
		{
			return;
		}

		client.setVar(VarClientStr.INPUT_TEXT, query);
		client.runScript(scriptArgs);
		Widget searchBackground = client.getWidget(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND);
		searchBackground.setSpriteId(SpriteID.EQUIPMENT_SLOT_TILE);
	}

	//must be called from clientThread
	private void buildMenus()
	{
		client.createMenuEntry(-1)
			.setOption("Add regex bookmark")
			.setIdentifier(1)
			.setType(MenuAction.RUNELITE)
			.onClick(this::editBookmark);

		List<String> keys = configmanager.getConfigurationKeys(CONFIG_GROUP + ".bookmark_");
		if (bookmark != null)
		{
			keys = keys.stream().filter((s) -> {
				String[] str = s.split("_", 2);
				if (str.length >= 2 && str[1].equals(bookmark.name))
				{
					return false;
				}
				return true;
			}).collect(Collectors.toList());

			client.createMenuEntry(-1)
				.setOption("Delete " + bookmark.name)
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					configmanager.unsetConfiguration(CONFIG_GROUP, "bookmark_" + bookmark.name);
					bookmark = null;
					clientThread.invoke(() -> layoutBank(""));
				});
		}
		for (String key : keys)
		{
			String[] str = key.split("_", 2);
			client.createMenuEntry(-1)
				.setOption(str[1])
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					final String query = configmanager.getConfiguration(CONFIG_GROUP, "bookmark_" + str[1]);
					bookmark = new Query(str[1], null, null);
					clientThread.invoke(() -> {
						layoutBank(query);
					});
				});
		}

		//No active bookmark
		if (bookmark == null)
		{
			return;
		}
		client.createMenuEntry(-1)
			.setOption("Close bookmark")
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				bookmark = null;
				clientThread.invoke(() -> layoutBank(""));
			});
	}
}
