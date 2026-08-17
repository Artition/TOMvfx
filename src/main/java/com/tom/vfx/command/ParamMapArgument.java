package com.tom.vfx.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Brigadier argument for a brace-wrapped parameter map: {@code {[name:value],[name:value]}}.
 * Parses into an ordered {@code Map<String, Float>} (e.g. {@code {[radius:8],[strength:0.5]}}).
 *
 * <p>The server never reads the parsed value back — the handler forwards the params over the
 * VFX network packet — so the network serializer is an empty template: the client re-parses
 * the command literal locally.</p>
 */
public final class ParamMapArgument implements ArgumentType<Map<String, Float>> {
	/** Mirrors {@code VFXTimeline.MAX_OVERRIDES} (command input, see AGENTS.md). */
	public static final int MAX_PARAMS = 32;

	private static final DynamicCommandExceptionType ERROR_TOO_MANY = new DynamicCommandExceptionType(
		count -> Component.translatable("commands.tompfx.too_many_params", count)
	);

	/** Empty-template serializer: the wire carries no data, both sides parse the literal themselves. */
	public static final class Info implements ArgumentTypeInfo<ParamMapArgument, Info.Template> {
		@Override
		public void serializeToNetwork(final Info.Template template, final FriendlyByteBuf buffer) {
		}

		@Override
		public Info.Template deserializeFromNetwork(final FriendlyByteBuf buffer) {
			return new Info.Template();
		}

		@Override
		public void serializeToJson(final Info.Template template, final com.google.gson.JsonObject json) {
			// ponytail: no JSON schema needed — vanilla only uses it for the /help dump.
		}

		@Override
		public Info.Template unpack(final ParamMapArgument argument) {
			return new Info.Template();
		}

		/** Stateless template. */
		public static final class Template implements ArgumentTypeInfo.Template<ParamMapArgument> {
			@Override
			public ParamMapArgument instantiate(final net.minecraft.commands.CommandBuildContext context) {
				return new ParamMapArgument();
			}

			@Override
			public ArgumentTypeInfo<ParamMapArgument, ?> type() {
				return new Info();
			}
		}
	}

	@Override
	public Map<String, Float> parse(final StringReader reader) throws CommandSyntaxException {
		final Map<String, Float> params = new LinkedHashMap<>();
		reader.expect('{');
		reader.skipWhitespace();
		if (reader.canRead() && reader.peek() == '}') {
			reader.skip();
			return params;
		}
		while (true) {
			reader.expect('[');
			final String name = reader.readStringUntil(':');
			final float value = reader.readFloat();
			reader.expect(']');
			if (params.size() >= MAX_PARAMS) {
				throw ERROR_TOO_MANY.create(MAX_PARAMS);
			}
			params.put(name, value);
			reader.skipWhitespace();
			if (reader.canRead() && reader.peek() == ',') {
				reader.skip();
				reader.skipWhitespace();
				continue;
			}
			break;
		}
		reader.skipWhitespace();
		reader.expect('}');
		return Collections.unmodifiableMap(params);
	}

	@Override
	public Collection<String> getExamples() {
		return List.of("{[radius:8]}", "{[radius:8],[strength:0.5]}");
	}
}
