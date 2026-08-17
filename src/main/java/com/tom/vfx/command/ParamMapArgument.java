package com.tom.vfx.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brigadier argument for a brace-wrapped parameter map: {@code {[name:value],[name:value]}}
 * (e.g. {@code {[radius:8],[target:0ab2...-uuid]}}).
 *
 * <p>Values are kept as raw strings (numeric params are parsed to floats by the command
 * handler; the special {@code target} param carries a UUID or player name). The parser is
 * <b>lenient</b>: an input that is still being typed (any prefix like {@code {[radius:} or
 * {@code {[radius:8],}) parses successfully with only the fully completed pairs collected,
 * so the attached suggestion provider keeps firing while the user types. Malformed
 * characters still stop the parse with a normal syntax error. An incomplete map that
 * somehow reaches execution simply applies zero pairs.</p>
 */
public final class ParamMapArgument implements ArgumentType<Map<String, String>> {
	/** Mirrors {@code VFXTimeline.MAX_OVERRIDES} (command input, see AGENTS.md). */
	public static final int MAX_PARAMS = 32;

	@Override
	public Map<String, String> parse(final StringReader reader) throws CommandSyntaxException {
		final Map<String, String> params = new LinkedHashMap<>();
		reader.expect('{');
		while (true) {
			final String name;
			final String value;
			final int pairStart = reader.getCursor();
			try {
				reader.expect('[');
				name = reader.readStringUntil(':');
				value = reader.readStringUntil(']');
			} catch (CommandSyntaxException e) {
				if (!reader.canRead()) {
					// Ran out of input mid-pair — an in-progress prefix, not an error.
					// Drop the partial pair and consume the typed text so the parse ends
					// cleanly at EOF (keeps live suggestions working).
					reader.setCursor(pairStart);
					while (reader.canRead()) {
						reader.skip();
					}
					break;
				}
				throw e;
			}
			if (params.size() < MAX_PARAMS) {
				params.put(name.trim(), value.trim());
			}
			if (!reader.canRead()) {
				break;
			}
			reader.skipWhitespace();
			if (!reader.canRead()) {
				break;
			}
			final char next = reader.peek();
			if (next == ',') {
				reader.skip();
				continue;
			}
			if (next == '}') {
				reader.skip();
				break;
			}
			break;
		}
		return params;
	}

	@Override
	public String toString() {
		return "vfx_param_map()";
	}
}
