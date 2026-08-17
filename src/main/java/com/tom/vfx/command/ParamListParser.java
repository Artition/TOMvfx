package com.tom.vfx.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Parses the {@code /vfx set} parameter-list syntax: {@code name:value[,name:value]} typed as a
 * single quoted string argument (e.g. {@code "radius:8,strength:0.5"}). Kept as a plain parser
 * over a vanilla string argument — a custom {@code ArgumentType} would need command-tree
 * network serialization, which is fragile across protocol changes.
 */
public final class ParamListParser {
	/** Mirrors {@code VFXTimeline.MAX_OVERRIDES} (command input, see AGENTS.md). */
	public static final int MAX_PARAMS = 32;

	private static final DynamicCommandExceptionType ERROR_TOO_MANY = new DynamicCommandExceptionType(
		count -> Component.translatable("commands.tompfx.too_many_params", count)
	);
	private static final DynamicCommandExceptionType ERROR_BAD_PAIR = new DynamicCommandExceptionType(
		pair -> Component.translatable("commands.tompfx.bad_param", pair)
	);

	private ParamListParser() {
	}

	/**
	 * Parses {@code name:value[,name:value]} into an ordered map.
	 *
	 * @param input the raw quoted-string content (without quotes)
	 * @return ordered parameter map
	 * @throws CommandSyntaxException on a pair without a colon or too many entries
	 */
	public static Map<String, Float> parse(final String input) throws CommandSyntaxException {
		final Map<String, Float> params = new LinkedHashMap<>();
		final StringReader reader = new StringReader(input);
		reader.skipWhitespace();
		if (!reader.canRead()) {
			return params;
		}
		while (true) {
			final int start = reader.getCursor();
			String name;
			try {
				name = reader.readStringUntil(':');
			} catch (CommandSyntaxException e) {
				throw ERROR_BAD_PAIR.create(input.substring(start).trim());
			}
			final float value = reader.readFloat();
			if (params.size() >= MAX_PARAMS) {
				throw ERROR_TOO_MANY.create(MAX_PARAMS);
			}
			params.put(name.trim(), value);
			reader.skipWhitespace();
			if (reader.canRead() && reader.peek() == ',') {
				reader.skip();
				reader.skipWhitespace();
				continue;
			}
			break;
		}
		reader.skipWhitespace();
		if (reader.canRead()) {
			throw ERROR_BAD_PAIR.create(reader.getRemaining());
		}
		return params;
	}
}
