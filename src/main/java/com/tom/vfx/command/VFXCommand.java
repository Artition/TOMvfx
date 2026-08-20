package com.tom.vfx.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tom.vfx.api.VFXAPI;
import com.tom.vfx.effect.EasingType;
import com.tom.vfx.effect.VFXCurveManager;
import com.tom.vfx.effect.VFXDefinition;
import com.tom.vfx.resource.VFXDefinitionManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /vfx play <effect> [<targets>]} triggers a VFX effect,
 * {@code /vfx stop <effect> [<targets>]} stops it and {@code /vfx list} lists known effects.
 */
public final class VFXCommand {
	private static final DynamicCommandExceptionType ERROR_UNKNOWN_EFFECT = new DynamicCommandExceptionType(
		id -> Component.translatable("commands.tompfx.unknown_effect", String.valueOf(id))
	);

	private VFXCommand() {
	}

	public static void register(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext context, final Commands.CommandSelection selection) {
		dispatcher.register(
			Commands.literal("vfx")
				.then(
					Commands.literal("play")
						.requires(VFXCommand::requirePermission)
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.executes(context2 -> play(context2, List.of(requirePlayer(context2))))
								.then(
									Commands.argument("targets", EntityArgument.players())
										.executes(context2 -> play(context2, EntityArgument.getPlayers(context2, "targets")))
								)
				)
			)
			.then(
				Commands.literal("playat")
					.requires(VFXCommand::requirePermission)
					.then(
						Commands.argument("effect", IdentifierArgument.id())
							.suggests(VFXCommand::suggestEffects)
							.then(
								Commands.argument("pos", BlockPosArgument.blockPos())
									.executes(context2 -> playAt(context2, List.of(requirePlayer(context2))))
									.then(
										Commands.argument("targets", EntityArgument.players())
											.executes(context2 -> playAt(context2, EntityArgument.getPlayers(context2, "targets")))
									)
							)
					)
			)
			.then(
				Commands.literal("stop")
						.requires(VFXCommand::requirePermission)
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.executes(context2 -> stop(context2, List.of(requirePlayer(context2))))
								.then(
									Commands.argument("targets", EntityArgument.players())
										.executes(context2 -> stop(context2, EntityArgument.getPlayers(context2, "targets")))
								)
						)
				)
				.then(
					Commands.literal("set")
						.requires(VFXCommand::requirePermission)
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.then(
									Commands.argument("params", new ParamMapArgument())
										.suggests(VFXCommand::suggestParamMap)
										.executes(context2 -> setParams(context2, List.of(requirePlayer(context2))))
										.then(
											Commands.argument("targets", EntityArgument.players())
												.executes(context2 -> setParams(context2, EntityArgument.getPlayers(context2, "targets")))
										)
								)
						)
				)
				.then(
					Commands.literal("key")
						.requires(VFXCommand::requirePermission)
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.then(
									Commands.argument("param", StringArgumentType.word())
										.suggests(VFXCommand::suggestEffectParams)
										.then(
											Commands.argument("time", IntegerArgumentType.integer(0))
												.then(
													Commands.argument("value", FloatArgumentType.floatArg())
														.executes(context2 -> keyframe(context2, EasingType.LINEAR.name(), List.of(requirePlayer(context2))))
														.then(
															Commands.argument("easing", StringArgumentType.word())
																.suggests(VFXCommand::suggestEasings)
																.executes(context2 -> keyframe(context2, currentEasing(context2), List.of(requirePlayer(context2))))
																.then(
																	Commands.argument("targets", EntityArgument.players())
																		.executes(context2 -> keyframe(context2, currentEasing(context2), EntityArgument.getPlayers(context2, "targets")))
																)
														)
														.then(
Commands.argument("targets", EntityArgument.players())
													.executes(context2 -> keyframe(context2, EasingType.LINEAR.name(), EntityArgument.getPlayers(context2, "targets")))
														)
												)
										)
								)
						)
				)
				.then(Commands.literal("list").executes(VFXCommand::list))
		);
	}

	/**
	 * Restricts the mutating subcommands ({@code play}/{@code playat}/{@code stop}/{@code set}/{@code key})
	 * to operators (gamemaster level 2). {@code list} stays available to everyone. This prevents a
	 * non-operator player from triggering or stopping VFX effects on other players' clients.
	 */
	private static boolean requirePermission(final CommandSourceStack source) {
		return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
	}

	private static int play(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		if (!VFXDefinitionManager.get().contains(effectId)) {
			throw ERROR_UNKNOWN_EFFECT.create(effectId.toString());
		}

		for (ServerPlayer player : targets) {
			VFXAPI.sendEffect(player, effectId, Map.of(), null);
		}

		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.played", effectId.toString(), targets.size()),
				false
			);
		return targets.size();
	}

	private static int playAt(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		if (!VFXDefinitionManager.get().contains(effectId)) {
			throw ERROR_UNKNOWN_EFFECT.create(effectId.toString());
		}
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		Vec3 worldPos = pos.getCenter();

		for (ServerPlayer player : targets) {
			VFXAPI.sendEffect(player, effectId, worldPos, Map.of(), null);
		}

		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.played_at", effectId.toString(), pos.getX(), pos.getY(), pos.getZ(), targets.size()),
				false
			);
		return targets.size();
	}

	private static int stop(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		for (ServerPlayer player : targets) {
			VFXAPI.sendStop(player, effectId);
		}
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.stopped", effectId.toString(), targets.size()),
				false
			);
		return targets.size();
	}

	private static int setParams(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		final Identifier effectId = IdentifierArgument.getId(context, "effect");
		@SuppressWarnings("unchecked")
		final Map<String, Float> params = context.getArgument("params", Map.class);
		for (final Map.Entry<String, Float> entry : params.entrySet()) {
			for (final ServerPlayer player : targets) {
				VFXAPI.sendSetParam(player, effectId, entry.getKey(), entry.getValue());
			}
		}
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.set", params.size(), effectId.toString(), targets.size()),
				false
			);
		return targets.size();
	}

	private static int keyframe(final CommandContext<CommandSourceStack> context, final String easing, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		String param = StringArgumentType.getString(context, "param");
		int time = IntegerArgumentType.getInteger(context, "time");
		float value = FloatArgumentType.getFloat(context, "value");
		for (ServerPlayer player : targets) {
			VFXAPI.sendKeyframe(player, effectId, param, time, value, easing);
		}
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.key", param, effectId.toString(), time, value, easing, targets.size()),
				false
			);
		return targets.size();
	}

	private static String currentEasing(final CommandContext<CommandSourceStack> context) {
		return StringArgumentType.getString(context, "easing");
	}

	private static int list(final CommandContext<CommandSourceStack> context) {
		VFXDefinitionManager definitions = VFXDefinitionManager.get();
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.list_count", definitions.getDefinitions().size())
					.append(Component.literal(" " + String.join(", ", definitions.getDefinitions().keySet().stream().map(Identifier::toString).toList()))),
				false
			);
		return definitions.getDefinitions().size();
	}

	private static CompletableFuture<Suggestions> suggestEffects(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggestResource(VFXDefinitionManager.get().getDefinitions().keySet().stream(), builder);
	}

	private static CompletableFuture<Suggestions> suggestEasings(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
		java.util.Set<String> names = new java.util.LinkedHashSet<>();
		for (EasingType type : EasingType.values()) {
			names.add(type.name().toLowerCase(java.util.Locale.ROOT));
		}
		names.addAll(VFXCurveManager.get().getCurves().keySet().stream().map(Identifier::toString).toList());
		return SharedSuggestionProvider.suggest(names, builder);
	}

	/**
	 * Suggests the parameter names of the effect referenced by the already-typed
	 * {@code effect} argument (definition params, or nothing when the id is unresolved).
	 */
	private static CompletableFuture<Suggestions> suggestEffectParams(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
		try {
			final Identifier effectId = context.getArgument("effect", Identifier.class);
			final VFXDefinition definition = VFXDefinitionManager.get().get(effectId);
			if (definition != null) {
				return SharedSuggestionProvider.suggest(definition.getParams().keySet(), builder);
			}
		} catch (IllegalArgumentException ignored) {
			// effect argument missing or not a valid id yet — nothing to suggest.
		}
		return builder.buildFuture();
	}

	/**
	 * Suggests the next token of the {@code {[name:value],...}} argument, walking the syntax:
	 * {@code {} after nothing, {@code [} at group start, {@code name:} inside a group,
	 * {@code ]} after a value, {@code ,} / {@code }} after a closed group. Suggestions are
	 * anchored at the current segment (via {@code createOffset}) so Brigadier filters them
	 * against the segment being typed, not the whole argument text.
	 */
	private static CompletableFuture<Suggestions> suggestParamMap(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
		java.util.Set<String> params;
		try {
			final Identifier effectId = context.getArgument("effect", Identifier.class);
			final VFXDefinition definition = VFXDefinitionManager.get().get(effectId);
			params = definition != null ? definition.getParams().keySet() : java.util.Set.of();
		} catch (IllegalArgumentException ignored) {
			params = java.util.Set.of();
		}

		final String remaining = builder.getRemaining();
		if (remaining.isEmpty()) {
			return SharedSuggestionProvider.suggest(List.of("{"), builder);
		}
		if (!remaining.startsWith("{")) {
			return builder.buildFuture();
		}
		// Anchor for tokens appended after everything typed so far.
		final SuggestionsBuilder atEnd = builder.createOffset(builder.getStart() + remaining.length());
		// Strip the opening brace: only the group after the last comma is being typed.
		final String content = remaining.substring(1);
		final String segment = content.substring(content.lastIndexOf(',') + 1);
		if (segment.isEmpty()) {
			return SharedSuggestionProvider.suggest(List.of("["), atEnd);
		}
		if (!segment.startsWith("[")) {
			return builder.buildFuture();
		}
		final int colon = segment.indexOf(':');
		if (colon < 0) {
			// Anchor right after the '[' (opening brace + previous groups + the bracket
			// itself), so the typed name prefix filters the suggestions.
			final int anchor = builder.getStart() + 1 + (content.length() - segment.length()) + 1;
			final SuggestionsBuilder atName = builder.createOffset(anchor);
			final List<String> names = new ArrayList<>(params.size());
			for (final String param : params) {
				names.add(param + ":");
			}
			return SharedSuggestionProvider.suggest(names, atName);
		}
		final String valuePart = segment.substring(colon + 1);
		if (valuePart.endsWith("]")) {
			return SharedSuggestionProvider.suggest(List.of(",", "}"), atEnd);
		}
		if (valuePart.isEmpty()) {
			return builder.buildFuture();
		}
		return SharedSuggestionProvider.suggest(List.of("]"), atEnd);
	}

	private static ServerPlayer requirePlayer(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return context.getSource().getPlayerOrException();
	}
}
