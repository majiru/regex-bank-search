package com.majiru.bankregex;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BankRegexSearchPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankRegexSearchPlugin.class);
		RuneLite.main(args);
	}
}