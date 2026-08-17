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
import com.tom.vfx.resource.VFXDefinitionManager;
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
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.then(
									Commands.argument("param", StringArgumentType.word())
										.then(
											Commands.argument("value", FloatArgumentType.floatArg())
												.executes(context2 -> setParam(context2, List.of(requirePlayer(context2))))
												.then(
													Commands.argument("targets", EntityArgument.players())
														.executes(context2 -> setParam(context2, EntityArgument.getPlayers(context2, "targets")))
												)
										)
								)
						)
				)
				.then(
					Commands.literal("key")
						.then(
							Commands.argument("effect", IdentifierArgument.id())
								.suggests(VFXCommand::suggestEffects)
								.then(
									Commands.argument("param", StringArgumentType.word())
										.then(
											Commands.argument("time", IntegerArgumentType.integer(0))
												.then(
													Commands.argument("value", FloatArgumentType.floatArg())
														.executes(context2 -> keyframe(context2, EasingType.LINEAR, List.of(requirePlayer(context2))))
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
																.executes(context2 -> keyframe(context2, EasingType.LINEAR, EntityArgument.getPlayers(context2, "targets")))
														)
												)
										)
								)
						)
				)
				.then(Commands.literal("list").executes(VFXCommand::list))
		);
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
		Map<String, Float> overrides = Map.of(
			"pos_x", (float) pos.getX(),
			"pos_y", (float) pos.getY(),
			"pos_z", (float) pos.getZ()
		);

		for (ServerPlayer player : targets) {
			VFXAPI.sendEffect(player, effectId, overrides, null);
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

	private static int setParam(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		String param = StringArgumentType.getString(context, "param");
		float value = FloatArgumentType.getFloat(context, "value");
		for (ServerPlayer player : targets) {
			VFXAPI.sendSetParam(player, effectId, param, value);
		}
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.set", param, effectId.toString(), value, targets.size()),
				false
			);
		return targets.size();
	}

	private static int keyframe(final CommandContext<CommandSourceStack> context, final EasingType easing, final Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Identifier effectId = IdentifierArgument.getId(context, "effect");
		String param = StringArgumentType.getString(context, "param");
		int time = IntegerArgumentType.getInteger(context, "time");
		float value = FloatArgumentType.getFloat(context, "value");
		for (ServerPlayer player : targets) {
			VFXAPI.sendKeyframe(player, effectId, param, time, value, easing);
		}
		context.getSource()
			.sendSuccess(
				() -> Component.translatable("commands.tompfx.key", param, effectId.toString(), time, value, easing.name(), targets.size()),
				false
			);
		return targets.size();
	}

	private static EasingType currentEasing(final CommandContext<CommandSourceStack> context) {
		return EasingType.fromString(StringArgumentType.getString(context, "easing"));
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
		return SharedSuggestionProvider.suggest(java.util.Arrays.stream(EasingType.values()).map(e -> e.name().toLowerCase(java.util.Locale.ROOT)), builder);
	}

	private static ServerPlayer requirePlayer(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return context.getSource().getPlayerOrException();
	}
}
